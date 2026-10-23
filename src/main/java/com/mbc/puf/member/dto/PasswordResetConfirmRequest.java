package com.mbc.puf.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PasswordResetConfirmRequest {
// 새 비번?
	// 공백 없는 ASCII 문자 8~64자. (BCrypt는 72바이트 초과 입력을 처리하지 못하므로 한글 등 멀티바이트는 제외)
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(regexp = "^[\\x21-\\x7E]{8,64}$", message = "비밀번호는 공백 없이 영문·숫자·특수문자 8~64자로 입력해주세요.")
    private String newPassword;

    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    private String newPasswordConfirm;
    
    public PasswordResetConfirmRequest() {
		// TODO Auto-generated constructor stub
	}

	public String getNewPassword() {
		return newPassword;
	}

	public void setNewPassword(String newPassword) {
		this.newPassword = newPassword;
	}

	public String getNewPasswordConfirm() {
		return newPasswordConfirm;
	}

	public void setNewPasswordConfirm(String newPasswordConfirm) {
		this.newPasswordConfirm = newPasswordConfirm;
	}
    
}
