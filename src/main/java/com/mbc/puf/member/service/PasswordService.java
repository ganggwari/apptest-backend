package com.mbc.puf.member.service;

import com.mbc.puf.member.dto.PhoneCodeTiming;

/** 비밀번호 재설정 (로그인 전): 아이디+전화번호 확인 → 문자 인증 → 새 비밀번호 */
public interface PasswordService {

    /** 인증코드 발송 응답에 실을 타이머 값(초). 고객/관리자 공통 */
    PhoneCodeTiming codeTiming();

    Long requestForCustomer(String loginId, String phone);
    void verifyForCustomer(Long customerId, String code);
    void confirmForCustomer(Long customerId, String newPassword, String newPasswordConfirm);

    Long requestForManager(String loginId, String phone);
    void verifyForManager(Long managerId, String code);
    void confirmForManager(Long managerId, String newPassword, String newPasswordConfirm);
}