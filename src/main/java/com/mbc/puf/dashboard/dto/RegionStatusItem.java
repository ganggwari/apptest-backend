package com.mbc.puf.dashboard.dto;

import java.math.BigDecimal;

/** MP-08 도별 통제현황의 한 항목. 지하차도가 하나도 없는 지역도 0건으로 포함된다.
 *  avgRiskScore/maxRiskScore는 최고관리자 전국 지도 색상 표시용 (지하차도가 없거나
 *  예측 이력이 전혀 없는 지역은 null) */
public class RegionStatusItem {

    private Long regionId;
    private String regionName;
    private long normalCount;
    private long blockedCount;
    private long totalCount;
    private BigDecimal avgRiskScore;
    private BigDecimal maxRiskScore;

    public RegionStatusItem() {
    }

    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }

    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }

    public long getNormalCount() { return normalCount; }
    public void setNormalCount(long normalCount) { this.normalCount = normalCount; }

    public long getBlockedCount() { return blockedCount; }
    public void setBlockedCount(long blockedCount) { this.blockedCount = blockedCount; }

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }

    public BigDecimal getAvgRiskScore() { return avgRiskScore; }
    public void setAvgRiskScore(BigDecimal avgRiskScore) { this.avgRiskScore = avgRiskScore; }

    public BigDecimal getMaxRiskScore() { return maxRiskScore; }
    public void setMaxRiskScore(BigDecimal maxRiskScore) { this.maxRiskScore = maxRiskScore; }
}