package com.mbc.puf.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 관리자 가입 3단계: 아이디/비밀번호 입력. 규칙은 고객 회원가입과 같다. */
public class ManagerSignupRequest {

    // 공백 없는 ASCII 문자(영문·숫자·특수문자) 6~50자
    @NotBlank(message = "아이디는 필수입니다.")
    @Pattern(regexp = "^[\\x21-\\x7E]{6,50}$", message = "아이디는 공백 없이 영문·숫자·특수문자 6~50자로 입력해 주세요.")
    private String loginId;

    // 공백 없는 ASCII 문자 8~64자. (BCrypt는 72바이트 초과 입력을 처리하지 못하므로 한글 등 멀티바이트는 제외)
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(regexp = "^[\\x21-\\x7E]{8,64}$", message = "비밀번호는 공백 없이 영문·숫자·특수문자 8~64자로 입력해 주세요.")
    private String password;

    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    private String passwordConfirm;

    public ManagerSignupRequest() {
    }

    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPasswordConfirm() { return passwordConfirm; }
    public void setPasswordConfirm(String passwordConfirm) { this.passwordConfirm = passwordConfirm; }
}
