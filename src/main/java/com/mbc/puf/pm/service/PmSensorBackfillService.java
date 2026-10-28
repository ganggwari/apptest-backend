package com.mbc.puf.pm.service;

import java.time.LocalDateTime;
import java.util.List;

import com.mbc.puf.pm.dto.PmSensorBackfillResponse;

/**
 * 센서 과거 데이터 채우기 (시연·개발용). 예지보전 설비마다 endAt 에서 weeks 주 전부터 센서 주기대로 값을 만든다.
 * 앞 normalWeeks 주는 정상 값만, 그 뒤부터 설비마다 정해진 이상 시나리오가 들어간다
 * (설비 종류별 번호 순서대로 위험 악화 → 주의 악화 → 정상 유지 → 일시적 튐 반복).
 */
public interface PmSensorBackfillService {

    /**
     * @param weeks       전체 주 수 (null 이면 설정 puf.pm.backfill.weeks, 기본 14)
     * @param normalWeeks 정상만 있는 앞 주 수 (null 이면 설정, 기본 4)
     * @param endAt       마지막 시각 (null 이면 지금). 센서 주기에 맞춰 내림
     * @param reset       true 면 대상 설비의 센서 데이터를 지우고 다시 만든다. false 면 센서 데이터가 이미 있는 설비는 건너뛴다
     * @param equipIds    대상 설비 (null 이면 삭제되지 않은 전체)
     */
    PmSensorBackfillResponse backfill(Integer weeks, Integer normalWeeks, LocalDateTime endAt, boolean reset, List<Long> equipIds);
    /** 최신 센서를 장비당 maxSamples개까지 추가. 기존 데이터는 수정하지 않는다. */
    int appendRecent(int maxSamples);
}

