package com.mbc.puf.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PasswordResetVerifyRequest {
// 여기서 리셋 verify request니까 id랑 인증 코드? 
	@NotBlank(message = "인증코드는 필수입니다.")
    @Pattern(regexp = "^\\d{6}$", message = "인증코드는 6자리 숫자입니다.")
    private String code;
	
	public PasswordResetVerifyRequest() {
		// TODO Auto-generated constructor stub
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}
	
}
