package com.mbc.puf.member.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.VerificationPurpose;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.member.dto.PhoneCodeTiming;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.sms.SmsSender;
import com.mbc.puf.common.util.VerificationCodeGenerator;
import com.mbc.puf.member.entity.PhoneVerify;
import com.mbc.puf.member.repository.PhoneVerifyRepository;

@Service
public class PhoneVerificationServiceImpl implements PhoneVerificationService {

    private static final VerificationPurpose SIGNUP = VerificationPurpose.SIGNUP;
    private static final VerificationPurpose RESET = VerificationPurpose.PASSWORD_RESET;

    private final PhoneVerifyRepository repository;
    private final DbClock dbClock;
    private final SmsSender smsSender;

    private final int codeTtlMinutes;          // 코드 유효 시간
    private final int resendCooldownSeconds;   // 재발송 대기 시간
    private final int verifiedValidMinutes;    // 인증 완료 후 가입/변경 제출까지 허용 시간

    public PhoneVerificationServiceImpl(PhoneVerifyRepository repository, DbClock dbClock, SmsSender smsSender,
            @Value("${app.phone-verification.code-ttl-minutes:3}") int codeTtlMinutes,
            @Value("${app.phone-verification.resend-cooldown-seconds:60}") int resendCooldownSeconds,
            @Value("${app.phone-verification.verified-valid-minutes:30}") int verifiedValidMinutes) {
        this.repository = repository;
        this.dbClock = dbClock;
        this.smsSender = smsSender;
        this.codeTtlMinutes = codeTtlMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.verifiedValidMinutes = verifiedValidMinutes;
    }

    @Override
    public PhoneCodeTiming timing() {
        return new PhoneCodeTiming(codeTtlMinutes * 60, resendCooldownSeconds);
    }

    // ==================== 가입 ====================

    @Override
    @Transactional
    public void sendSignupCode(String phone) {
        LocalDateTime now = dbClock.now();
        assertCooldown(repository.countByPhoneAndPurposeAndCreatedAtAfter(phone, SIGNUP,
                now.minusSeconds(resendCooldownSeconds)));

        PhoneVerify verify = new PhoneVerify();
        verify.setPhone(phone);
        issue(verify, SIGNUP, now, "[PUF] 회원가입 인증코드");
    }

    @Override
    @Transactional
    public void verifySignupCode(String phone, String code) {
        check(repository.findFirstByPhoneAndPurposeOrderByIdDesc(phone, SIGNUP), code);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertSignupVerified(String phone) {
        if (repository.countByPhoneAndPurposeAndVerifiedAtAfter(phone, SIGNUP, verifiedSince()) == 0) {
            throw badRequest("전화번호 인증이 필요합니다. 인증 후 " + verifiedValidMinutes + "분 이내에 가입을 완료해 주세요.");
        }
    }

    // ==================== 비밀번호 재설정 ====================

    @Override
    @Transactional
    public void sendResetCode(ResetTarget target, String phone) {
        LocalDateTime now = dbClock.now();
        LocalDateTime since = now.minusSeconds(resendCooldownSeconds);
        assertCooldown(target.customerId() != null
                ? repository.countByCustomerIdAndPurposeAndCreatedAtAfter(target.customerId(), RESET, since)
                : repository.countByManagerIdAndPurposeAndCreatedAtAfter(target.managerId(), RESET, since));

        PhoneVerify verify = new PhoneVerify();
        verify.setCustomerId(target.customerId());
        verify.setManagerId(target.managerId());
        verify.setPhone(phone);
        issue(verify, RESET, now, "[PUF] 비밀번호 재설정 인증코드");
    }

    @Override
    @Transactional
    public void verifyResetCode(ResetTarget target, String code) {
        check(target.customerId() != null
                ? repository.findFirstByCustomerIdAndPurposeOrderByIdDesc(target.customerId(), RESET)
                : repository.findFirstByManagerIdAndPurposeOrderByIdDesc(target.managerId(), RESET), code);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertResetVerified(ResetTarget target) {
        long count = target.customerId() != null
                ? repository.countByCustomerIdAndPurposeAndVerifiedAtAfter(target.customerId(), RESET, verifiedSince())
                : repository.countByManagerIdAndPurposeAndVerifiedAtAfter(target.managerId(), RESET, verifiedSince());
        if (count == 0) {
            throw badRequest("전화번호 인증이 필요합니다. 인증 후 " + verifiedValidMinutes + "분 이내에 비밀번호를 변경해 주세요.");
        }
    }

    // ---- 내부 메서드 ----

    private void assertCooldown(long recentCount) {
        if (recentCount > 0) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "인증코드는 " + resendCooldownSeconds + "초 후에 다시 요청할 수 있습니다.");
        }
    }

    // 코드 발급 → 저장 → 문자 발송. 발송이 실패하면 예외가 나서 저장도 함께 롤백된다
    private void issue(PhoneVerify verify, VerificationPurpose purpose, LocalDateTime now, String title) {
        String code = VerificationCodeGenerator.generate();
        verify.setPurpose(purpose);
        verify.setCode(code);
        verify.setExpiresAt(now.plusMinutes(codeTtlMinutes));   // 만료 시각도 DB 시계 기준
        repository.save(verify);
        smsSender.send(verify.getPhone(), title + " [" + code + "] " + codeTtlMinutes + "분 이내에 입력해 주세요.");
    }

    // TODO(2차): 코드 입력 실패 횟수 제한 (6자리 코드 무차별 대입 방지)
    private void check(Optional<PhoneVerify> latest, String code) {
        PhoneVerify verify = latest.orElse(null);
        if (verify == null) {
            throw badRequest("인증코드를 먼저 요청해 주세요.");
        }
        if (verify.getVerifiedAt() != null) {
            return;   // 이미 인증 완료 (중복 클릭 등) → 성공으로 처리
        }
        LocalDateTime now = dbClock.now();
        if (!verify.getExpiresAt().isAfter(now)) {   // expires_at <= NOW()
            throw badRequest("인증코드가 만료되었습니다. 다시 요청해 주세요.");
        }
        if (!verify.getCode().equals(code == null ? null : code.trim())) {
            throw badRequest("인증코드가 일치하지 않습니다.");
        }
        verify.setVerifiedAt(now);
    }

    private LocalDateTime verifiedSince() {
        return dbClock.now().minusMinutes(verifiedValidMinutes);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}