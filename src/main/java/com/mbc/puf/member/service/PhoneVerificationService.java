package com.mbc.puf.member.service;

import com.mbc.puf.member.dto.PhoneCodeTiming;

/**
 * 전화번호 인증코드 발송·확인 (phone_verify). 고객 가입, 관리자 가입, 비밀번호 재설정이 같이 쓴다.
 * 전화번호는 숫자만 있는 값(PhoneNumber.normalize 결과)으로 넘긴다.
 */
public interface PhoneVerificationService {

    /** 인증코드 발송 응답에 실을 타이머 값 (유효시간, 재발송 대기시간을 초로) */
    PhoneCodeTiming timing();

    // ---- 가입: 전화번호 기준 ----
    void sendSignupCode(String phone);

    void verifySignupCode(String phone, String code);

    // 인증 완료 후 일정 시간 안인지 (가입 제출 직전에 확인)
    void assertSignupVerified(String phone);

    // ---- 비밀번호 재설정: 계정 기준 (고객/관리자 중 하나) ----
    void sendResetCode(ResetTarget target, String phone);

    void verifyResetCode(ResetTarget target, String code);

    void assertResetVerified(ResetTarget target);

    /** 비밀번호 재설정 대상 계정. customerId 와 managerId 중 하나만 값이 있다 */
    record ResetTarget(Long customerId, Long managerId) {
        public static ResetTarget customer(Long id) {
            return new ResetTarget(id, null);
        }

        public static ResetTarget manager(Long id) {
            return new ResetTarget(null, id);
        }
    }
}