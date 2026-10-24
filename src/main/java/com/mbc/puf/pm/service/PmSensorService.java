package com.mbc.puf.pm.service;

import java.time.LocalDateTime;
import java.util.List;

import com.mbc.puf.pm.dto.PmSensorSeriesResponse;

/** 예지보전 센서 데이터 조회 (예지보전 서버가 모델 입력 창을 만들 때 호출) */
public interface PmSensorService {

    /**
     * 장비별 센서 데이터를 버킷(시간 구간)으로 묶어 집계해서 돌려준다.
     *
     * @param equipIds  장비 아이디 (1~50개)
     * @param from      이 시각 이상 (null 이면 제한 없음)
     * @param to        이 시각 미만 (null 이면 지금)
     * @param bucketSec 묶는 크기(초). null 이면 각 장비의 센서 주기(= 원본 그대로)
     * @param after     이 시각보다 뒤의 버킷만 (null 이면 제한 없음)
     * @param limit     받을 버킷 수 상한 (null 이면 20000, 100~50000)
     */
    PmSensorSeriesResponse getSensorSeries(List<Long> equipIds, LocalDateTime from, LocalDateTime to, Integer bucketSec,
            LocalDateTime after, Integer limit);
}
