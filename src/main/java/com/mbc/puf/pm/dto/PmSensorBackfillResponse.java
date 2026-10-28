package com.mbc.puf.pm.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.mbc.puf.common.enums.PmEquipmentType;

/**
 * 센서 과거 데이터 채우기 결과.
 *  startAt ~ endAt : 만든 기간(종류별로 펌프만 짧을 수 있어 각 종류의 startAt 은 byType 에 따로 있다)
 *  skipped         : 이미 센서 데이터가 있어서 건너뛴 설비 수 (reset=true 면 건너뛰지 않고 지우고 다시 만든다)
 */
public record PmSensorBackfillResponse(
        LocalDateTime endAt,
        int weeks,
        int normalWeeks,
        boolean reset,
        int skipped,
        List<TypeResult> byType) {

    public record TypeResult(PmEquipmentType type, int equipments, long rows, LocalDateTime startAt, long elapsedMs) {
    }
}
