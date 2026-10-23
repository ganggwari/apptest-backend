package com.mbc.puf.weather.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 지하차도 예보 한 시점 (FastAPI 침수 모델의 예보 입력용). 가장 가까운 날씨 지역의 시간별 예보 */
public record UnderpassForecastItem(LocalDateTime targetAt, BigDecimal temperatureC, Integer humidityPct,
                                    BigDecimal rainfallMm, Integer precipitationProb) {}
