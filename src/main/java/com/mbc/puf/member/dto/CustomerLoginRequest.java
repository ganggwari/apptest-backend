package com.mbc.puf.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 고객 로그인 요청 (형식 규칙은 알려주지 않기 위해 빈 값/길이만 검사) */
public class CustomerLoginRequest {

    @NotBlank(message = "아이디를 입력해 주세요.")
    @Size(max = 50, message = "아이디는 50자 이하입니다.")
    private String loginId;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(max = 64, message = "비밀번호는 64자 이하입니다.")   // BCrypt 72바이트 제한 방어
    private String password;

    public CustomerLoginRequest() {
    }

    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
