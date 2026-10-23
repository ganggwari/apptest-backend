package com.mbc.puf.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 비밀번호 재설정 1단계: 아이디 + 가입한 전화번호가 맞으면 그 번호로 인증코드를 보낸다 (고객·관리자 공용) */
public class PasswordResetRequestRequest {

    @NotBlank(message = "아이디는 필수입니다.")
    private String loginId;

    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(regexp = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)")
    private String phone;

    public PasswordResetRequestRequest() {
    }

    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
