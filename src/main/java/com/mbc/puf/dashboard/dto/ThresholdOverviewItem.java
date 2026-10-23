package com.mbc.puf.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mbc.puf.common.enums.RiskLevel;

/** 최고관리자 "전체 임계치 현황표"의 한 줄. 지하차도 하나당 보통 3줄(WARNING/DANGER 등 레벨별)이 나온다 */
public class ThresholdOverviewItem {

    private Long underpassId;
    private String underpassName;
    private String regionName;
    private RiskLevel level;
    private BigDecimal riskScoreMin;
    private LocalDateTime updatedAt;

    public ThresholdOverviewItem() {
    }

    public Long getUnderpassId() { return underpassId; }
    public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }

    public String getUnderpassName() { return underpassName; }
    public void setUnderpassName(String underpassName) { this.underpassName = underpassName; }

    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }

    public RiskLevel getLevel() { return level; }
    public void setLevel(RiskLevel level) { this.level = level; }

    public BigDecimal getRiskScoreMin() { return riskScoreMin; }
    public void setRiskScoreMin(BigDecimal riskScoreMin) { this.riskScoreMin = riskScoreMin; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}