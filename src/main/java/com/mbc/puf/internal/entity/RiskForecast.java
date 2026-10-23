package com.mbc.puf.internal.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 위험도 미래 예측 (테이블: risk_forecast). 현재~+6시간 정도를 시점별로 담고, 같은 시점은 덮어쓴다 */
@Entity
@Table(name = "risk_forecast")
public class RiskForecast {

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

    @Column(name = "target_at")
    private LocalDateTime targetAt;

    @Column(name = "predicted_at")
    private LocalDateTime predictedAt;

    public RiskForecast() {
    }

    public Long getId() { return id; }
    public Long getUnderpassId() { return underpassId; }
    public BigDecimal getRiskScore() { return riskScore; }
    public BigDecimal getRainfallMm() { return rainfallMm; }
    public LocalDateTime getTargetAt() { return targetAt; }
    public LocalDateTime getPredictedAt() { return predictedAt; }
}
