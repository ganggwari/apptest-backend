package com.mbc.puf.pm.service;

import java.time.LocalDateTime;
import java.util.List;

import com.mbc.puf.common.enums.PmEquipmentType;

/**
 * 센서 데이터 미수신 감시. 센서 테이블의 마지막 측정 시각이 (센서 주기 × 배수, 최소 초) 넘게 오래됐으면 "미수신" 이벤트를 만든다.
 * 센서 데이터가 한 번도 시작되지 않은 설비(data_started_at 이 null)는 보지 않는다.
 */
public interface PmStaleService {

    /** 설비 1대의 센서 신선도 */
    record Freshness(Long equipId, String name, PmEquipmentType type, Long underpassId, String underpassName, Long regionId,
            int intervalSec, int thresholdSec, LocalDateTime lastAt, boolean stale, boolean handled) {
    }

    /**
     * 센서 데이터가 시작된 설비들의 신선도. regionIds 가 null 이면 전체.
     * handled = 이번 끊김(마지막 측정 이후)에 대해 이미 미수신 이벤트를 만들었음(처리 중이든 끝났든)
     */
    List<Freshness> freshness(List<Long> regionIds);

    /** 끊긴 설비마다 미수신 이벤트·알림을 만든다 (이번 끊김에 이미 만들었으면 건너뜀). 새로 만든 이벤트 수 */
    int raiseNoDataEvents();
}
