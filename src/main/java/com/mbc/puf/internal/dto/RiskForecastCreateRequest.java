package com.mbc.puf.internal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** FastAPI가 보내는 위험도 미래 예측 (POST /api/internal/risk-forecasts).
 *  한 지하차도의 앞으로 몇 시간치를 한 번에 보낸다. 같은 시점은 덮어쓰고, 지난 시점 행은 서버가 지운다 */
public class RiskForecastCreateRequest {

    @NotNull(message = "underpassId는 필수입니다.")
    private Long underpassId;

    @NotNull(message = "predictedAt은 필수입니다.")
    private LocalDateTime predictedAt;

    @NotEmpty(message = "items는 1개 이상이어야 합니다.")
    @Size(max = 48, message = "items는 최대 48개까지 보낼 수 있습니다.")
    private List<@Valid Item> items;

    public RiskForecastCreateRequest() {
    }

    public Long getUnderpassId() { return underpassId; }
    public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }

    public LocalDateTime getPredictedAt() { return predictedAt; }
    public void setPredictedAt(LocalDateTime predictedAt) { this.predictedAt = predictedAt; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    /** 시점 하나의 예측. 강수량은 그 시점 기준(예보 기반)이며 값이 없으면 생략 가능 */
    public static class Item {

        @NotNull(message = "targetAt은 필수입니다.")
        private LocalDateTime targetAt;

        @NotNull(message = "riskScore는 필수입니다.")
        @DecimalMin(value = "0.00", message = "riskScore는 0 이상이어야 합니다.")
        @DecimalMax(value = "100.00", message = "riskScore는 100 이하여야 합니다.")
        private BigDecimal riskScore;

        @DecimalMin(value = "0.00", message = "rainfallMm은 0 이상이어야 합니다.")
        private BigDecimal rainfallMm;
        @DecimalMin(value = "0.00", message = "rainfall1hMm은 0 이상이어야 합니다.")
        private BigDecimal rainfall1hMm;
        @DecimalMin(value = "0.00", message = "rainfall3hMm은 0 이상이어야 합니다.")
        private BigDecimal rainfall3hMm;
        @DecimalMin(value = "0.00", message = "rainfall6hMm은 0 이상이어야 합니다.")
        private BigDecimal rainfall6hMm;
        @DecimalMin(value = "0.00", message = "rainfall12hMm은 0 이상이어야 합니다.")
        private BigDecimal rainfall12hMm;
        @DecimalMin(value = "0.00", message = "rainfall24hMm은 0 이상이어야 합니다.")
        private BigDecimal rainfall24hMm;

        public Item() {
        }

        public LocalDateTime getTargetAt() { return targetAt; }
        public void setTargetAt(LocalDateTime targetAt) { this.targetAt = targetAt; }

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
    }
}
