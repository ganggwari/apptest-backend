package com.mbc.puf.pm.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 원인 센서 시계열 (GET /api/pm/equipments/{id}/sensor-trend). 값은 1시간 평균, 띠(band)는 이 설비의 처음 28일 평균 ± 2σ */
public record PmSensorTrendResponse(String column, List<Point> points, Band band) {

    /** 1시간 평균 한 점 */
    public record Point(LocalDateTime at, Double value) {
    }

    /** 평소 범위. 기준 데이터가 모자라면 band 자체가 null */
    public record Band(Double mean, Double sd, Double low, Double high, LocalDateTime baselineFrom, LocalDateTime baselineTo) {
    }
}
