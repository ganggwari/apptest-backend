package com.mbc.puf.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 고객 가입용 전화번호 인증코드 확인 요청 */
public class PhoneCodeVerifyRequest {

    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(regexp = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)")
    private String phone;

    @NotBlank(message = "인증코드를 입력해 주세요.")
    @Pattern(regexp = "^\\d{6}$", message = "인증코드는 숫자 6자리입니다.")
    private String code;

    public PhoneCodeVerifyRequest() {
    }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
