package com.mbc.puf.handover.dto;

import java.time.LocalDateTime;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.enums.UnderpassCurrentStatus;

/** 인수인계 집계용 쿼리 결과 행 (NativeSql 이 컬럼 별칭으로 채운다) */
public final class HandoverRows {

    private HandoverRows() {
    }

    /** 지하차도 이벤트 (차단·해제·위험 단계 변화) */
    public static class EventRow {
        private LocalDateTime createdAt;
        private String type;               // BLOCK / RELEASE / RISK_LEVEL_UP / RISK_LEVEL_DOWN
        private RiskLevel riskLevel;
        private String underpassName;
        private Boolean automatic;
        private String reason;
        private String operatorName;       // 수동 차단·해제를 한 직원 (자동이면 null)

        public String getOperatorName() { return operatorName; }
        public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public RiskLevel getRiskLevel() { return riskLevel; }
        public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
        public String getUnderpassName() { return underpassName; }
        public void setUnderpassName(String underpassName) { this.underpassName = underpassName; }
        public Boolean getAutomatic() { return automatic; }
        public void setAutomatic(Boolean automatic) { this.automatic = automatic; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /** 전광판 문구를 직원이 직접 수정한 기록 */
    public static class DisplayEditRow {
        private LocalDateTime changedAt;
        private String underpassName;
        private String newContent;
        private String actorName;

        public LocalDateTime getChangedAt() { return changedAt; }
        public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
        public String getUnderpassName() { return underpassName; }
        public void setUnderpassName(String underpassName) { this.underpassName = underpassName; }
        public String getNewContent() { return newContent; }
        public void setNewContent(String newContent) { this.newContent = newContent; }
        public String getActorName() { return actorName; }
        public void setActorName(String actorName) { this.actorName = actorName; }
    }

    /** 직원별 처리 건수 (kind: block / release / plateReview / pmAction / displayEdit / transfer / inquiryAnswer) */
    public static class StaffCountRow {
        private Long managerId;
        private String kind;
        private Long cnt;

        public Long getManagerId() { return managerId; }
        public void setManagerId(Long managerId) { this.managerId = managerId; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public Long getCnt() { return cnt; }
        public void setCnt(Long cnt) { this.cnt = cnt; }
    }

    /** 직원 기본 정보 */
    public static class StaffInfoRow {
        private Long managerId;
        private String name;
        private String phone;

        public Long getManagerId() { return managerId; }
        public void setManagerId(Long managerId) { this.managerId = managerId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    /** 지하차도 지금 상태 */
    public static class UnderpassRow {
        private Long underpassId;
        private String name;
        private RiskLevel riskLevel;
        private UnderpassCurrentStatus status;

        public Long getUnderpassId() { return underpassId; }
        public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public RiskLevel getRiskLevel() { return riskLevel; }
        public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
        public UnderpassCurrentStatus getStatus() { return status; }
        public void setStatus(UnderpassCurrentStatus status) { this.status = status; }
    }
}
