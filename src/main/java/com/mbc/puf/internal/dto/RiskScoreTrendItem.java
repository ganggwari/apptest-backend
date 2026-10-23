package com.mbc.puf.internal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** MP-05 위험도 추이의 한 점. 시간 오름차순으로 정렬해서 반환한다(그래프에 바로 그리도록) */
public class RiskScoreTrendItem {

    private BigDecimal riskScore;
    private BigDecimal rainfallMm;
    private BigDecimal rainfall1hMm;
    private BigDecimal rainfall3hMm;
    private BigDecimal rainfall6hMm;
    private BigDecimal rainfall12hMm;
    private BigDecimal rainfall24hMm;
    private LocalDateTime predictedAt;

    public RiskScoreTrendItem() {
    }

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