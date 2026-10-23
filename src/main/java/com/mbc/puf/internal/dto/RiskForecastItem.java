package com.mbc.puf.internal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 위험도 예측 그래프의 미래 구간 한 점 (GET /api/underpasses/{id}/risk-forecast) */
public record RiskForecastItem(LocalDateTime targetAt, BigDecimal riskScore, BigDecimal rainfallMm,
                               LocalDateTime predictedAt) {}
