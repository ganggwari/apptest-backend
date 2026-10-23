package com.mbc.puf.member.dto;

import java.util.List;

import com.mbc.puf.common.enums.ManagerRole;

/** 관리자 내 정보 (MP-03). 이름은 직원 명단 값이라 수정 불가, 전화번호·이메일은 수정 가능 */
public class ManagerProfileResponse {

    private final Long id;
    private final String loginId;
    private final String name;
    private final String email;
    private final String phone;
    private final ManagerRole role;
    private final List<RegionRef> regions;   // 담당 지역 (없으면 빈 목록)
    private final ShiftResponse shift;       // 소속 근무조 (없으면 null)

    public ManagerProfileResponse(Long id, String loginId, String name, String email, String phone,
                                  ManagerRole role, List<RegionRef> regions, ShiftResponse shift) {
        this.id = id;
        this.loginId = loginId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.regions = regions;
        this.shift = shift;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public ManagerRole getRole() { return role; }
    public List<RegionRef> getRegions() { return regions; }
    public ShiftResponse getShift() { return shift; }
}
