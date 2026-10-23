package com.mbc.puf.threshold.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;

/** 임계치 변경 이력 (테이블: threshold_history) */
@Entity
@Table(name = "threshold_history")
public class ThresholdHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "threshold_id")
    private Long thresholdId;

    @Column(name = "old_min")
    private BigDecimal oldRiskScoreMin;

    @Column(name = "new_min")
    private BigDecimal newRiskScoreMin;

    @Column(name = "changed_by")
    private Long changedBy;

    @Generated   // DB 기본값(NOW())으로 INSERT 시 채워지고, 저장 직후 값을 다시 읽어온다
    @Column(name = "changed_at", insertable = false, updatable = false)
    private LocalDateTime changedAt;

    public ThresholdHistory() {
    }

    public Long getId() {
        return id;
    }

    public Long getThresholdId() {
        return thresholdId;
    }

    public void setThresholdId(Long thresholdId) {
        this.thresholdId = thresholdId;
    }

    public BigDecimal getOldRiskScoreMin() {
        return oldRiskScoreMin;
    }

    public void setOldRiskScoreMin(BigDecimal oldRiskScoreMin) {
        this.oldRiskScoreMin = oldRiskScoreMin;
    }

    public BigDecimal getNewRiskScoreMin() {
        return newRiskScoreMin;
    }

    public void setNewRiskScoreMin(BigDecimal newRiskScoreMin) {
        this.newRiskScoreMin = newRiskScoreMin;
    }

    public Long getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(Long changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
