package com.mbc.puf.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 위험도 추이(기간별) 그래프의 하루 치 포인트. regionId가 null이면 전국 기준으로 집계된다.
 *  예측 이력이 전혀 없는 날은 결과 자체에 없음(0으로 채워지지 않음 — 프론트에서 빈 날짜 보간 필요) */
public class RiskTrendPoint {

    private LocalDate trendDate;
    private BigDecimal avgRiskScore;
    private BigDecimal maxRiskScore;

    public RiskTrendPoint() {
    }

    public LocalDate getTrendDate() { return trendDate; }
    public void setTrendDate(LocalDate trendDate) { this.trendDate = trendDate; }

    public BigDecimal getAvgRiskScore() { return avgRiskScore; }
    public void setAvgRiskScore(BigDecimal avgRiskScore) { this.avgRiskScore = avgRiskScore; }

    public BigDecimal getMaxRiskScore() { return maxRiskScore; }
    public void setMaxRiskScore(BigDecimal maxRiskScore) { this.maxRiskScore = maxRiskScore; }
}
