package com.mbc.puf.dashboard.dto;

import java.math.BigDecimal;

/** 최고관리자 "위험 임박 TOP" 목록의 한 줄. margin = dangerThreshold - riskScore
 *  (0 이하면 이미 DANGER 임계치를 넘긴 것, DB에서 미리 계산해서 내려준다 — 정렬도 margin ASC로 서버가 함) */
public class RiskMarginItem {

    private Long underpassId;
    private String underpassName;
    private String regionName;
    private BigDecimal riskScore;
    private BigDecimal dangerThreshold;
    private BigDecimal margin;

    public RiskMarginItem() {
    }

    public Long getUnderpassId() { return underpassId; }
    public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }

    public String getUnderpassName() { return underpassName; }
    public void setUnderpassName(String underpassName) { this.underpassName = underpassName; }

    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }

    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }

    public BigDecimal getDangerThreshold() { return dangerThreshold; }
    public void setDangerThreshold(BigDecimal dangerThreshold) { this.dangerThreshold = dangerThreshold; }

    public BigDecimal getMargin() { return margin; }
    public void setMargin(BigDecimal margin) { this.margin = margin; }
}