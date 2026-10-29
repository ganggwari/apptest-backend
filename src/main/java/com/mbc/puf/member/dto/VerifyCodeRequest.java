package com.mbc.puf.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 인증코드만 보내는 확인 요청 (관리자 가입·비밀번호 재설정처럼 번호를 세션이 기억하고 있는 경우) */
public class VerifyCodeRequest {

    @NotBlank(message = "인증코드를 입력해 주세요.")
    @Pattern(regexp = "^\\d{6}$", message = "인증코드는 숫자 6자리입니다.")
    private String code;

    public VerifyCodeRequest() {
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
