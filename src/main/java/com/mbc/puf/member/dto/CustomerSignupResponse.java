package com.mbc.puf.member.dto;

/** 고객 회원가입 결과 (승인 절차 없이 즉시 활성화되므로 별도 상태 없음) */
public class CustomerSignupResponse {

    private final Long id;
    private final String loginId;
    private final String name;

    public CustomerSignupResponse(Long id, String loginId, String name) {
        this.id = id;
        this.loginId = loginId;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getName() { return name; }
}
