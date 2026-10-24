package com.mbc.puf.handover.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 근무조 인수인계 리포트 (테이블: shift_handover). summary = 작성 시점 자동 집계 스냅샷(JSON 문자열), memo = 수기 메모 */
@Entity
@Table(name = "shift_handover")
public class ShiftHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "shift_id")
    private Long shiftId;

    @Column(name = "manager_id")
    private Long managerId;

    @Column(name = "shift_date")
    private LocalDate shiftDate;       // 그 근무조가 시작한 날 (야간조는 시작한 날짜)

    @Column(name = "summary")
    private String summary;

    @Column(name = "memo")
    private String memo;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public ShiftHandover() {
    }

    public Long getId() { return id; }

    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }

    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }

    public LocalDate getShiftDate() { return shiftDate; }
    public void setShiftDate(LocalDate shiftDate) { this.shiftDate = shiftDate; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
