package com.mbc.puf.threshold.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** 임계치 수정 요청. level은 URL 경로로 받으므로 여기 없다 */
public class ThresholdUpdateRequest {

    @NotNull(message = "risk_score_min 값은 필수입니다.")
    @DecimalMin(value = "0.00", message = "risk_score_min은 0 이상이어야 합니다.")
    @DecimalMax(value = "100.00", message = "risk_score_min은 100 이하여야 합니다.")   // threshold.risk_score_min DECIMAL(5,2), 0~100 표시점수 기준
    private BigDecimal riskScoreMin;

    public ThresholdUpdateRequest() {
    }

    public BigDecimal getRiskScoreMin() { return riskScoreMin; }
    public void setRiskScoreMin(BigDecimal riskScoreMin) { this.riskScoreMin = riskScoreMin; }
}