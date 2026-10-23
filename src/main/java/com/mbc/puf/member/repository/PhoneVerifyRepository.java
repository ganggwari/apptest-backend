package com.mbc.puf.member.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mbc.puf.common.enums.VerificationPurpose;
import com.mbc.puf.member.entity.PhoneVerify;

/**
 * 전화번호 인증코드 발송 기록. "최근 N초/N분" 비교의 기준 시각(since)은 서비스가 DbClock(DB 시계)으로 계산해서 넘긴다.
 * 가입(SIGNUP)은 전화번호 기준, 비밀번호 재설정(PASSWORD_RESET)은 customerId/managerId 기준으로 찾는다.
 */
public interface PhoneVerifyRepository extends JpaRepository<PhoneVerify, Long> {

    // ---- 가입(SIGNUP): 전화번호 기준 ----
    long countByPhoneAndPurposeAndCreatedAtAfter(String phone, VerificationPurpose purpose, LocalDateTime since);

    Optional<PhoneVerify> findFirstByPhoneAndPurposeOrderByIdDesc(String phone, VerificationPurpose purpose);

    long countByPhoneAndPurposeAndVerifiedAtAfter(String phone, VerificationPurpose purpose, LocalDateTime since);

    // ---- 비밀번호 재설정: 고객 ----
    long countByCustomerIdAndPurposeAndCreatedAtAfter(Long customerId, VerificationPurpose purpose, LocalDateTime since);

    Optional<PhoneVerify> findFirstByCustomerIdAndPurposeOrderByIdDesc(Long customerId, VerificationPurpose purpose);

    long countByCustomerIdAndPurposeAndVerifiedAtAfter(Long customerId, VerificationPurpose purpose, LocalDateTime since);

    // ---- 비밀번호 재설정: 관리자 ----
    long countByManagerIdAndPurposeAndCreatedAtAfter(Long managerId, VerificationPurpose purpose, LocalDateTime since);

    Optional<PhoneVerify> findFirstByManagerIdAndPurposeOrderByIdDesc(Long managerId, VerificationPurpose purpose);

    long countByManagerIdAndPurposeAndVerifiedAtAfter(Long managerId, VerificationPurpose purpose, LocalDateTime since);
}
