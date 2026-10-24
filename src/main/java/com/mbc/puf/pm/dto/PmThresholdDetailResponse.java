package com.mbc.puf.pm.dto;

import java.util.List;

/** 설비 1개의 단계 기준 + 최근 변경 이력 (GET /api/pm/equipments/{equipId}/thresholds) */
public record PmThresholdDetailResponse(PmThresholdItem threshold, List<PmThresholdHistoryItem> history) {
}
