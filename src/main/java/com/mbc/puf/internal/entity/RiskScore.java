package com.mbc.puf.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 위험도 예측 결과 (테이블: risk_score) */
@Entity
@Table(name = "risk_score")
public class RiskScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "underpass_id")
    private Long underpassId;

    @Column(name = "score")
    private BigDecimal riskScore;

    @Column(name = "rain")
    private BigDecimal rainfallMm;

    @Column(name = "rain_1h")
    private BigDecimal rainfall1hMm;

    @Column(name = "rain_3h")
    private BigDecimal rainfall3hMm;

    @Column(name = "rain_6h")
    private BigDecimal rainfall6hMm;

    @Column(name = "rain_12h")
    private BigDecimal rainfall12hMm;

    @Column(name = "rain_24h")
    private BigDecimal rainfall24hMm;

    @Column(name = "predicted_at")
    private LocalDateTime predictedAt;

    public RiskScore() {
    }

    public Long getId() {
        return id;
    }

    public Long getUnderpassId() {
        return underpassId;
    }

    public void setUnderpassId(Long underpassId) {
        this.underpassId = underpassId;
    }

    public BigDecimal getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(BigDecimal riskScore) {
        this.riskScore = riskScore;
    }

    public BigDecimal getRainfallMm() {
        return rainfallMm;
    }

    public void setRainfallMm(BigDecimal rainfallMm) {
        this.rainfallMm = rainfallMm;
    }

    public BigDecimal getRainfall1hMm() {
        return rainfall1hMm;
    }

    public void setRainfall1hMm(BigDecimal rainfall1hMm) {
        this.rainfall1hMm = rainfall1hMm;
    }

    public BigDecimal getRainfall3hMm() {
        return rainfall3hMm;
    }

    public void setRainfall3hMm(BigDecimal rainfall3hMm) {
        this.rainfall3hMm = rainfall3hMm;
    }

    public BigDecimal getRainfall6hMm() {
        return rainfall6hMm;
    }

    public void setRainfall6hMm(BigDecimal rainfall6hMm) {
        this.rainfall6hMm = rainfall6hMm;
    }

    public BigDecimal getRainfall12hMm() {
        return rainfall12hMm;
    }

    public void setRainfall12hMm(BigDecimal rainfall12hMm) {
        this.rainfall12hMm = rainfall12hMm;
    }

    public BigDecimal getRainfall24hMm() {
        return rainfall24hMm;
    }

    public void setRainfall24hMm(BigDecimal rainfall24hMm) {
        this.rainfall24hMm = rainfall24hMm;
    }

    public LocalDateTime getPredictedAt() {
        return predictedAt;
    }

    public void setPredictedAt(LocalDateTime predictedAt) {
        this.predictedAt = predictedAt;
    }
}
