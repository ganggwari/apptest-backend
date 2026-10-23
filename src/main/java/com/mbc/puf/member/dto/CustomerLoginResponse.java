package com.mbc.puf.member.dto;

/** 고객 로그인 결과. 로그인 상태는 세션 쿠키(JSESSIONID)로 유지되므로 토큰은 없다. */
public class CustomerLoginResponse {

    private final Long id;
    private final String loginId;
    private final String name;

    public CustomerLoginResponse(Long id, String loginId, String name) {
        this.id = id;
        this.loginId = loginId;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getName() { return name; }
}
