package com.mbc.puf.member.dto;

import jakarta.validation.constraints.NotBlank;

/** 회원 탈퇴 요청. 본인 확인을 위해 비밀번호를 한 번 더 받는다 (고객·관리자 공용) */
public class WithdrawRequest {

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    private String password;

    public WithdrawRequest() {
    }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
