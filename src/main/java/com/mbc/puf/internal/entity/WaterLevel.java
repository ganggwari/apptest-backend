package com.mbc.puf.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 수위계 측정값 (테이블: water_level). 지하차도당 센서 1개의 단일값(cm) */
@Entity
@Table(name = "water_level")
public class WaterLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "underpass_id")
    private Long underpassId;

    @Column(name = "level_cm")
    private BigDecimal levelCm;

    @Column(name = "measured_at")
    private LocalDateTime measuredAt;

    public WaterLevel() {
    }

    public Long getId() { return id; }

    public Long getUnderpassId() { return underpassId; }
    public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }

    public BigDecimal getLevelCm() { return levelCm; }
    public void setLevelCm(BigDecimal levelCm) { this.levelCm = levelCm; }

    public LocalDateTime getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(LocalDateTime measuredAt) { this.measuredAt = measuredAt; }
}
