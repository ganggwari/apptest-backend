package com.mbc.puf.member.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.mbc.puf.common.enums.ManagerRole;
import com.mbc.puf.common.enums.ManagerStatus;

/** 관리자 목록(MP-12)의 한 항목이자, 지역·근무조 배정과 탈퇴 처리 후 돌려주는 계정 요약 */
public class ManagerSummaryResponse {

    private final Long id;
    private final String loginId;
    private final String name;
    private final String email;
    private final String phone;
    private final ManagerRole role;
    private final ManagerStatus status;
    private final List<RegionRef> regions;
    private final Long shiftId;
    private final String shiftName;
    private final LocalDateTime createdAt;
    private final LocalDateTime approvedAt;

    public ManagerSummaryResponse(Long id, String loginId, String name, String email, String phone,
                                  ManagerRole role, ManagerStatus status, List<RegionRef> regions,
                                  Long shiftId, String shiftName, LocalDateTime createdAt, LocalDateTime approvedAt) {
        this.id = id;
        this.loginId = loginId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.status = status;
        this.regions = regions;
        this.shiftId = shiftId;
        this.shiftName = shiftName;
        this.createdAt = createdAt;
        this.approvedAt = approvedAt;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public ManagerRole getRole() { return role; }
    public ManagerStatus getStatus() { return status; }
    public List<RegionRef> getRegions() { return regions; }
    public Long getShiftId() { return shiftId; }
    public String getShiftName() { return shiftName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
}
