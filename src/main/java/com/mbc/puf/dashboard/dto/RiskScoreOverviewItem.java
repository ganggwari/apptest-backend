package com.mbc.puf.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 지하차도별 최신 위험점수 1건 (목록 화면 스파크라인/배지용). 아직 예측 이력이 없는 지하차도는
 *  riskScore/predictedAt이 null로 나온다 (LEFT JOIN) */
public class RiskScoreOverviewItem {

    private Long underpassId;
    private String underpassName;
    private BigDecimal riskScore;
    private LocalDateTime predictedAt;

    public RiskScoreOverviewItem() {
    }

    public Long getUnderpassId() { return underpassId; }
    public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }

    public String getUnderpassName() { return underpassName; }
    public void setUnderpassName(String underpassName) { this.underpassName = underpassName; }

    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }

    public LocalDateTime getPredictedAt() { return predictedAt; }
    public void setPredictedAt(LocalDateTime predictedAt) { this.predictedAt = predictedAt; }
}