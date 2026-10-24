package com.mbc.puf.pm.dto;

/** 단계별 설비 수. noData = 점수를 한 번도 못 받았거나 기준 시각 직전 일정 시간 동안 못 받은 설비 */
public record PmLevelCounts(int safe, int caution, int danger, int noData) {
}
