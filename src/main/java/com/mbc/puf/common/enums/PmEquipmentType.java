package com.mbc.puf.common.enums;

/** 예지보전 설비 종류 (DB: pm_equipment_type). WATER_LEVEL 은 1.puf_db_schema_v9.sql 부터 포함 */
public enum PmEquipmentType {
    CCTV,         // CCTV
    DISPLAY,      // 전광판
    PUMP,         // 배수펌프
    ROAD,         // 노면 (영상 분석 대상, 센서 고장 점수와 별도)
    WATER_LEVEL   // 수위센서 (수위계 장비 자체의 고장 여부. 물 높이 측정값은 water_level 테이블)
}
