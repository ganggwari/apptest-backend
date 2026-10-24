package com.mbc.puf.pm.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mbc.puf.common.enums.PmEquipmentType;

/** 설비별 단계 기준 1행 (GET /api/pm/thresholds). NativeSql 이 컬럼 별칭으로 채운다 */
public class PmThresholdItem {

    private Long equipId;
    private String name;                // 설비 이름 (예: P-12-1)
    private PmEquipmentType type;
    private Long underpassId;
    private String underpassName;
    private Long regionId;
    private BigDecimal cautionMin;       // 이 점수부터 주의
    private BigDecimal dangerMin;        // 이 점수부터 위험
    private LocalDateTime updatedAt;     // 마지막 수정 시각
    private String updatedByName;        // 마지막 수정한 관리자 (직원 이름)

    public PmThresholdItem() {
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /** name 과 같은 값 (프런트가 code 를 쓰던 때와 호환) */
    public String getCode() { return name; }

    /** 안전은 항상 0점부터 */
    public BigDecimal getSafeMin() {
        return BigDecimal.ZERO;
    }

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

    public BigDecimal getCautionMin() { return cautionMin; }
    public void setCautionMin(BigDecimal cautionMin) { this.cautionMin = cautionMin; }

    public BigDecimal getDangerMin() { return dangerMin; }
    public void setDangerMin(BigDecimal dangerMin) { this.dangerMin = dangerMin; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedByName() { return updatedByName; }
    public void setUpdatedByName(String updatedByName) { this.updatedByName = updatedByName; }
}
