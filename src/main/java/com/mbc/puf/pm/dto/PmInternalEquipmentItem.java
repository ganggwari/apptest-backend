package com.mbc.puf.pm.dto;

import java.time.LocalDateTime;

/** 예지보전 서버가 받아 가는 설비 1건 (GET /api/internal/pm/equipments) */
public class PmInternalEquipmentItem {

    private final Long equipId;
    private final String name;                  // 설비 이름 (예: P-12-1)
    private final String type;                  // CCTV / DISPLAY / PUMP / WATER_LEVEL
    private final Long underpassId;
    private final String underpassName;
    private final Integer sensorIntervalSec;    // 센서 측정 주기(초)
    private final LocalDateTime dataStartedAt;  // 센서 데이터가 시작된 시각 (아직 없으면 null)
    private final LocalDateTime lastSensorAt;   // 센서 데이터의 마지막 측정 시각 (아직 없으면 null). 예지보전 서버가 이 시각 기준으로 최근 창을 잘라 간다

    public PmInternalEquipmentItem(Long equipId, String name, String type, Long underpassId, String underpassName,
            Integer sensorIntervalSec, LocalDateTime dataStartedAt, LocalDateTime lastSensorAt) {
        this.equipId = equipId;
        this.name = name;
        this.type = type;
        this.underpassId = underpassId;
        this.underpassName = underpassName;
        this.sensorIntervalSec = sensorIntervalSec;
        this.dataStartedAt = dataStartedAt;
        this.lastSensorAt = lastSensorAt;
    }

    public Long getEquipId() {
        return equipId;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Long getUnderpassId() {
        return underpassId;
    }

    public String getUnderpassName() {
        return underpassName;
    }

    public Integer getSensorIntervalSec() {
        return sensorIntervalSec;
    }

    public LocalDateTime getDataStartedAt() {
        return dataStartedAt;
    }

    public LocalDateTime getLastSensorAt() {
        return lastSensorAt;
    }
}
