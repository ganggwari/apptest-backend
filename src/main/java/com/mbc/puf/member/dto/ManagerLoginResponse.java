package com.mbc.puf.member.dto;

import java.util.List;

import com.mbc.puf.common.enums.ManagerRole;

/** 관리자 로그인 결과. 로그인 상태는 세션 쿠키(JSESSIONID)로 유지된다. regions = 담당 지역 목록(여러 개 가능) */
public class ManagerLoginResponse {

    private final Long id;
    private final String loginId;
    private final String name;
    private final ManagerRole role;
    private final List<RegionRef> regions;

    public ManagerLoginResponse(Long id, String loginId, String name, ManagerRole role, List<RegionRef> regions) {
        this.id = id;
        this.loginId = loginId;
        this.name = name;
        this.role = role;
        this.regions = regions;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getName() { return name; }
    public ManagerRole getRole() { return role; }
    public List<RegionRef> getRegions() { return regions; }
}
