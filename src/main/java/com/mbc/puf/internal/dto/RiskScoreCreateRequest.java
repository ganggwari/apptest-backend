package com.mbc.puf.internal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** FastAPI가 보내는 위험도 예측 결과 (POST /api/internal/risk-scores).
 *  강수량 6개는 AI 모델의 실제 입력값(현재/1시간/3·6·12·24시간 누적)과 1:1로 대응한다 */
public class RiskScoreCreateRequest {

    @NotNull(message = "underpassId는 필수입니다.")
    private Long underpassId;

    @NotNull(message = "riskScore는 필수입니다.")
    @DecimalMin(value = "0.00", message = "riskScore는 0 이상이어야 합니다.")
    @DecimalMax(value = "100.00", message = "riskScore는 100 이하여야 합니다.")   // risk_score.risk_score DECIMAL(5,2). AI 모델 표시점수(0~100) 기준, 확률(0~1) 아님
    private BigDecimal riskScore;

    // 관측/API 실패로 값이 없을 수 있어 필수로 두지 않음. 있으면 0 이상이어야 함
    @DecimalMin(value = "0.00", message = "rainfallMm은 0 이상이어야 합니다.")
    private BigDecimal rainfallMm;

    // 관측/API 실패로 값이 없을 수 있어 필수로 두지 않음. 있으면 0 이상이어야 함
    @DecimalMin(value = "0.00", message = "rainfall1hMm은 0 이상이어야 합니다.")
    private BigDecimal rainfall1hMm;

    // 관측/API 실패로 값이 없을 수 있어 필수로 두지 않음. 있으면 0 이상이어야 함
    @DecimalMin(value = "0.00", message = "rainfall3hMm은 0 이상이어야 합니다.")
    private BigDecimal rainfall3hMm;

    // 관측/API 실패로 값이 없을 수 있어 필수로 두지 않음. 있으면 0 이상이어야 함
    @DecimalMin(value = "0.00", message = "rainfall6hMm은 0 이상이어야 합니다.")
    private BigDecimal rainfall6hMm;

    // 관측/API 실패로 값이 없을 수 있어 필수로 두지 않음. 있으면 0 이상이어야 함
    @DecimalMin(value = "0.00", message = "rainfall12hMm은 0 이상이어야 합니다.")
    private BigDecimal rainfall12hMm;

    // 관측/API 실패로 값이 없을 수 있어 필수로 두지 않음. 있으면 0 이상이어야 함
    @DecimalMin(value = "0.00", message = "rainfall24hMm은 0 이상이어야 합니다.")
    private BigDecimal rainfall24hMm;

    @NotNull(message = "predictedAt은 필수입니다.")
    private LocalDateTime predictedAt;

    public RiskScoreCreateRequest() {
    }

    public Long getUnderpassId() { return underpassId; }
    public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }

    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }

    public BigDecimal getRainfallMm() { return rainfallMm; }
    public void setRainfallMm(BigDecimal rainfallMm) { this.rainfallMm = rainfallMm; }

    public BigDecimal getRainfall1hMm() { return rainfall1hMm; }
    public void setRainfall1hMm(BigDecimal rainfall1hMm) { this.rainfall1hMm = rainfall1hMm; }

    public BigDecimal getRainfall3hMm() { return rainfall3hMm; }
    public void setRainfall3hMm(BigDecimal rainfall3hMm) { this.rainfall3hMm = rainfall3hMm; }

    public BigDecimal getRainfall6hMm() { return rainfall6hMm; }
    public void setRainfall6hMm(BigDecimal rainfall6hMm) { this.rainfall6hMm = rainfall6hMm; }

    public BigDecimal getRainfall12hMm() { return rainfall12hMm; }
    public void setRainfall12hMm(BigDecimal rainfall12hMm) { this.rainfall12hMm = rainfall12hMm; }

    public BigDecimal getRainfall24hMm() { return rainfall24hMm; }
    public void setRainfall24hMm(BigDecimal rainfall24hMm) { this.rainfall24hMm = rainfall24hMm; }

    public LocalDateTime getPredictedAt() { return predictedAt; }
    public void setPredictedAt(LocalDateTime predictedAt) { this.predictedAt = predictedAt; }
}