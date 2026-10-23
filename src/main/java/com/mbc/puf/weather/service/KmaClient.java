package com.mbc.puf.weather.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.mbc.puf.weather.entity.WeatherArea;

/**
 * 기상청 API 접점. Spring 이 기상청을 직접 호출해 값을 받아오는 자리다.
 * 지금은 실제 호출 구현이 없고, 시연용 가짜 구현(DemoKmaClient)만 있다.
 * 실제 구현을 붙일 때는 이 인터페이스만 구현하면 저장/조회 쪽은 그대로 쓴다.
 */
public interface KmaClient {

    /** 지역의 현재 관측값 (기온·습도·강수량). 못 가져오면 null */
    Observation fetchObservation(WeatherArea area);

    /** 지역의 앞으로의 시간별 예보 (시간 오름차순). 못 가져오면 빈 목록 */
    List<ForecastPoint> fetchForecast(WeatherArea area);

    record Observation(BigDecimal temp, Integer humidity, BigDecimal rain, LocalDateTime observedAt) {}

    record ForecastPoint(LocalDateTime targetAt, BigDecimal temp, Integer humidity, BigDecimal rain,
                         Integer rainProb, String sky) {}
}
