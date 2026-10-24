package com.mbc.puf.pm.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mbc.puf.common.enums.PmLevel;

/** 단계 기준 변경 이력 1행 (설비 기준 상세의 history) */
public class PmThresholdHistoryItem {

    private PmLevel level;             // 어느 단계의 기준이 바뀌었는지 (주의/위험)
    private BigDecimal oldMin;         // 변경 전 (처음 만들어졌으면 null)
    private BigDecimal newMin;         // 변경 후
    private Long changedBy;
    private String changedByName;      // 변경한 관리자 (직원 이름)
    private LocalDateTime changedAt;

    public PmThresholdHistoryItem() {
    }

    public PmLevel getLevel() { return level; }
    public void setLevel(PmLevel level) { this.level = level; }

    public BigDecimal getOldMin() { return oldMin; }
    public void setOldMin(BigDecimal oldMin) { this.oldMin = oldMin; }

    public BigDecimal getNewMin() { return newMin; }
    public void setNewMin(BigDecimal newMin) { this.newMin = newMin; }

    public Long getChangedBy() { return changedBy; }
    public void setChangedBy(Long changedBy) { this.changedBy = changedBy; }

    public String getChangedByName() { return changedByName; }
    public void setChangedByName(String changedByName) { this.changedByName = changedByName; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
}
