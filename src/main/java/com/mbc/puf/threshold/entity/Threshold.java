package com.mbc.puf.threshold.entity;

import com.mbc.puf.common.enums.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** 지하차도별 위험도 임계치 (테이블: threshold) */
@Entity
@Table(name = "threshold")
public class Threshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "underpass_id")
    private Long underpassId;

    @Column(name = "level")
    private RiskLevel level;

    @Column(name = "min_score")
    private BigDecimal riskScoreMin;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Generated(event = EventType.INSERT)   // INSERT 땐 DB 기본값(NOW()), 수정 시엔 서비스가 DbClock 시각을 넣는다
    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    public Threshold() {
    }

    public Long getId() {
        return id;
    }

    public Long getUnderpassId() {
        return underpassId;
    }

    public void setUnderpassId(Long underpassId) {
        this.underpassId = underpassId;
    }

    public RiskLevel getLevel() {
        return level;
    }

    public void setLevel(RiskLevel level) {
        this.level = level;
    }

    public BigDecimal getRiskScoreMin() {
        return riskScoreMin;
    }

    public void setRiskScoreMin(BigDecimal riskScoreMin) {
        this.riskScoreMin = riskScoreMin;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
