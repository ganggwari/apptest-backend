package com.mbc.puf.member.dto;

/** 근무조 배정 (MP-12). shiftId 가 null 이면 근무조 없음(교대 근무 안 함) */
public class ManagerShiftRequest {

    private Long shiftId;

    public ManagerShiftRequest() {
    }

    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
}
