package com.mbc.puf.pm.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.mbc.puf.common.enums.PmEquipmentType;

/**
 * 예지보전 엔진 상태 (GET /api/pm/engine-status).
 *  status            : WAITING(켠 뒤 점수를 아직 못 받음) / NORMAL / DELAYED / DOWN
 *  lastReceivedAt    : 마지막으로 점수 묶음을 받은 시각 (서버 메모리 기준, 서버를 다시 켜면 null)
 *  equipmentTotal    : 내 담당 지역의 설비 수,  scored : 점수를 한 번이라도 받은 설비 수
 *  sensorStale       : 지금 센서 데이터가 끊긴 설비 수,  openNoDataEvents : 처리 중인 미수신 이벤트 수
 */
public record PmEngineStatusResponse(
        String status,
        LocalDateTime lastReceivedAt,
        Long secondsSinceReceived,
        int lastBatchSize,
        boolean lastBatchBackfill,
        int equipmentTotal,
        int scored,
        int sensorStale,
        int openNoDataEvents,
        List<TypeStatus> byType) {

    /** latestPredictedAt : 그 종류의 가장 최근 점수 시각,  latestSensorAt : 가장 최근 센서 측정 시각 */
    public record TypeStatus(PmEquipmentType type, int total, int scored, int sensorStale,
            LocalDateTime latestPredictedAt, LocalDateTime latestSensorAt) {
    }
}
