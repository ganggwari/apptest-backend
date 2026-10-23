package com.mbc.puf.threshold.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mbc.puf.common.enums.RiskLevel;

/** 임계치 응답 (목록/수정결과 공용) */
public class ThresholdResponse {

    private final Long id;
    private final Long underpassId;
    private final RiskLevel level;
    private final BigDecimal riskScoreMin;
    private final Long updatedBy;
    private final LocalDateTime updatedAt;

    public ThresholdResponse(Long id, Long underpassId, RiskLevel level, BigDecimal riskScoreMin,
                             Long updatedBy, LocalDateTime updatedAt) {
        this.id = id;
        this.underpassId = underpassId;
        this.level = level;
        this.riskScoreMin = riskScoreMin;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getUnderpassId() { return underpassId; }
    public RiskLevel getLevel() { return level; }
    public BigDecimal getRiskScoreMin() { return riskScoreMin; }
    public Long getUpdatedBy() { return updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
