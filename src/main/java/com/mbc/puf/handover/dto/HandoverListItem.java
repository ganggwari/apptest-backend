package com.mbc.puf.handover.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;


/** 인수인계 리포트 목록 1행 (GET /api/shift-handovers). 집계 내용은 상세에서 */
public class HandoverListItem {

    private Long id;
    private Long shiftId;
    private String shiftName;
    private LocalDate shiftDate;
    private Long managerId;
    private String authorName;
    private String memo;
    private LocalDateTime createdAt;

    public HandoverListItem() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }

    public String getShiftName() { return shiftName; }
    public void setShiftName(String shiftName) { this.shiftName = shiftName; }

    public LocalDate getShiftDate() { return shiftDate; }
    public void setShiftDate(LocalDate shiftDate) { this.shiftDate = shiftDate; }

    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
