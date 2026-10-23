package com.mbc.puf.weather.service;

import java.math.BigDecimal;

import com.mbc.puf.weather.dto.UnderpassRainfallResponse;
import com.mbc.puf.weather.dto.WeatherResponse;

public interface WeatherService {

    // 위경도에서 가장 가까운 지역의 현재 날씨 + 시간별(24시간) + 일별(7일) 예보 (CP-08)
    WeatherResponse getWeather(BigDecimal lat, BigDecimal lng);

    // 지하차도 강수 (FastAPI 침수 모델 입력용). 지하차도가 없으면 BusinessException(404)
    UnderpassRainfallResponse getUnderpassRainfall(Long underpassId);

    // 지하차도 예보 (FastAPI 침수 모델 입력용). 가장 가까운 날씨 지역의 앞으로 24시간 시간별 예보. 지하차도가 없으면 404
    java.util.List<com.mbc.puf.weather.dto.UnderpassForecastItem> getUnderpassForecast(Long underpassId);
}
