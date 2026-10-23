package com.mbc.puf.internal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 위험도 수신 결과 응답 */
public class RiskScoreResponse {

    private final Long id;
    private final Long underpassId;
    private final BigDecimal riskScore;
    private final LocalDateTime predictedAt;

    public RiskScoreResponse(Long id, Long underpassId, BigDecimal riskScore, LocalDateTime predictedAt) {
        this.id = id;
        this.underpassId = underpassId;
        this.riskScore = riskScore;
        this.predictedAt = predictedAt;
    }

    public Long getId() { return id; }
    public Long getUnderpassId() { return underpassId; }
    public BigDecimal getRiskScore() { return riskScore; }
    public LocalDateTime getPredictedAt() { return predictedAt; }
}
