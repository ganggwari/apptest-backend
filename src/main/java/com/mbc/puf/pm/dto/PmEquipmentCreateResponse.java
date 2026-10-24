package com.mbc.puf.pm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.mbc.puf.common.enums.PmEquipmentType;

/** 예지보전 설비 등록 결과. 단계 기준(주의·위험)은 기본값으로 만들어진 값이다 */
public record PmEquipmentCreateResponse(
        Long equipId,
        String name,                    // DB 가 정한 설비 이름 (예: P-12-1)
        PmEquipmentType type,
        Long underpassId,
        String underpassName,
        Long camId,
        Long itsVmsId,
        LocalDate installedAt,
        Integer sensorIntervalSec,      // 종류별 기본 센서 주기(초)
        BigDecimal cautionMin,          // 주의 기준 (기본 40)
        BigDecimal dangerMin) {         // 위험 기준 (기본 70)
}
