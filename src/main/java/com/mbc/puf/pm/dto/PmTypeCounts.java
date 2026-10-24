package com.mbc.puf.pm.dto;

import com.mbc.puf.common.enums.PmEquipmentType;

/** 설비 종류별 단계별 설비 수 */
public record PmTypeCounts(PmEquipmentType type, int total, int safe, int caution, int danger, int noData) {
}
