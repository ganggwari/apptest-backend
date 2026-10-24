package com.mbc.puf.weather.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 기상청 실제 날씨를 지역별로 서버 메모리에 들고 있는다 (프런트 날씨 화면 표출 전용, DB에 저장하지 않음).
 * KmaDisplayScheduler 가 채우고 WeatherServiceImpl.getWeather 가 읽는다. 서버를 다시 켜면 비워졌다가 첫 수집(약 10초 뒤)에 다시 채워진다.
 * 침수모델(FastAPI)이 읽는 weather / weather_forecast(시연 stub)와는 완전히 별개다.
 */
@Component
public class KmaWeatherStore {

    private final Map<Long, KmaClient.Observation> observations = new ConcurrentHashMap<>();
    private final Map<Long, List<KmaClient.ForecastPoint>> forecasts = new ConcurrentHashMap<>();

    public void putObservation(Long areaId, KmaClient.Observation observation) {
        if (observation != null) {
            observations.put(areaId, observation);
        }
    }

    /** 새 예보로 통째로 바꾼다 (빈 목록이면 이전 예보를 그대로 둔다) */
    public void putForecast(Long areaId, List<KmaClient.ForecastPoint> points) {
        if (points != null && !points.isEmpty()) {
            forecasts.put(areaId, List.copyOf(points));
        }
    }

    /** 가장 최근 관측값. 없거나 maxAgeHours 보다 오래됐으면 null (오래된 값을 현재 날씨로 보여주지 않으려고) */
    public KmaClient.Observation observation(Long areaId, LocalDateTime now, int maxAgeHours) {
        KmaClient.Observation o = observations.get(areaId);
        if (o == null || o.observedAt() == null || o.observedAt().isBefore(now.minusHours(maxAgeHours))) {
            return null;
        }
        return o;
    }

    /** now 이후 예보 (시간 오름차순) */
    public List<KmaClient.ForecastPoint> forecast(Long areaId, LocalDateTime now) {
        return forecasts.getOrDefault(areaId, List.of()).stream()
                .filter(p -> p.targetAt().isAfter(now))
                .sorted((a, b) -> a.targetAt().compareTo(b.targetAt()))
                .toList();
    }
}
