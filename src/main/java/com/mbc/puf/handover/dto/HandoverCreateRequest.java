package com.mbc.puf.handover.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

/** 인수인계 리포트 작성 요청 (POST /api/shift-handovers). shiftId·shiftDate 를 안 주면 내 근무조의 지금 근무 */
public class HandoverCreateRequest {

    private Long shiftId;              // 근무조 (생략하면 내 근무조)
    private LocalDate shiftDate;       // 그 근무조가 시작한 날 (생략하면 지금 근무 중인 날)

    @Size(max = 2000, message = "memo는 2000자 이하여야 합니다.")
    private String memo;               // 수기 메모

    public HandoverCreateRequest() {
    }

    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }

    public LocalDate getShiftDate() { return shiftDate; }
    public void setShiftDate(LocalDate shiftDate) { this.shiftDate = shiftDate; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
}
