package com.mbc.puf.internal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** 수위계가 보내는 측정값 (POST /api/internal/water-levels). 지하차도당 센서 1개의 단일값 */
public class WaterLevelCreateRequest {

    @NotNull(message = "underpassId는 필수입니다.")
    private Long underpassId;

    @NotNull(message = "levelCm은 필수입니다.")
    @DecimalMin(value = "0.0", message = "levelCm은 0 이상이어야 합니다.")
    @DecimalMax(value = "9999.9", message = "levelCm이 너무 큽니다.")   // water_level.level_cm DECIMAL(5,1)
    private BigDecimal levelCm;

    @NotNull(message = "measuredAt은 필수입니다.")
    private LocalDateTime measuredAt;

    public WaterLevelCreateRequest() {
    }

    public Long getUnderpassId() { return underpassId; }
    public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }

    public BigDecimal getLevelCm() { return levelCm; }
    public void setLevelCm(BigDecimal levelCm) { this.levelCm = levelCm; }

    public LocalDateTime getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(LocalDateTime measuredAt) { this.measuredAt = measuredAt; }
}
