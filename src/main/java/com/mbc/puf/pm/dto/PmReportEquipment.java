package com.mbc.puf.pm.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.enums.PmEventStatus;
import com.mbc.puf.common.enums.PmLevel;

/** 리포트에 나오는 설비 1행: 어느 시점 기준 최근 점수·단계와 처리 중인 점검 상태. NativeSql 이 컬럼 별칭으로 채운다 */
public class PmReportEquipment {

    private Long equipId;
    private String name;                // 설비 이름 (예: P-12-1)
    private PmEquipmentType type;
    private Long underpassId;
    private String underpassName;
    private Long regionId;
    private BigDecimal score;                 // 그 시점의 최근 점수 (없으면 null)
    private PmLevel level;
    private LocalDateTime lastReceivedAt;     // 그 점수가 들어온 시각
    private Integer sensorIntervalSec = 3600;   // 센서 측정 주기(초). 미수신 기준 계산용
    private Long openEventId;                 // 처리 중인 점검 대기 (없으면 null)
    private PmEventStatus openEventStatus;

    public PmReportEquipment() {
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /** name 과 같은 값 (프런트가 code 를 쓰던 때와 호환) */
    public String getCode() { return name; }

    public Integer getSensorIntervalSec() { return sensorIntervalSec; }
    public void setSensorIntervalSec(Integer sensorIntervalSec) { this.sensorIntervalSec = sensorIntervalSec; }

    public Long getEquipId() { return equipId; }
    public void setEquipId(Long equipId) { this.equipId = equipId; }

    public PmEquipmentType getType() { return type; }
    public void setType(PmEquipmentType type) { this.type = type; }

    public Long getUnderpassId() { return underpassId; }
    public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }

    public String getUnderpassName() { return underpassName; }
    public void setUnderpassName(String underpassName) { this.underpassName = underpassName; }

    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public PmLevel getLevel() { return level; }
    public void setLevel(PmLevel level) { this.level = level; }

    public LocalDateTime getLastReceivedAt() { return lastReceivedAt; }
    public void setLastReceivedAt(LocalDateTime lastReceivedAt) { this.lastReceivedAt = lastReceivedAt; }

    public Long getOpenEventId() { return openEventId; }
    public void setOpenEventId(Long openEventId) { this.openEventId = openEventId; }

    public PmEventStatus getOpenEventStatus() { return openEventStatus; }
    public void setOpenEventStatus(PmEventStatus openEventStatus) { this.openEventStatus = openEventStatus; }
}
