package com.mbc.puf.member.dto;

import com.mbc.puf.common.enums.ManagerStatus;

/** 관리자 가입 신청 결과. 항상 승인대기(PENDING) 상태로 생성된다. */
public class ManagerSignupResponse {

    private final Long id;
    private final String loginId;
    private final ManagerStatus status;

    public ManagerSignupResponse(Long id, String loginId, ManagerStatus status) {
        this.id = id;
        this.loginId = loginId;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public ManagerStatus getStatus() { return status; }
}
