package com.mbc.puf.weather.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import com.mbc.puf.weather.dto.UnderpassForecastItem;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.weather.dto.UnderpassRainfallResponse;
import com.mbc.puf.weather.service.WeatherService;

/** FastAPI 전용 날씨 API. /api/internal/** 이라 세션 대신 API 키(X-Internal-Api-Key)로 인증한다
 *  (검사는 WebConfigurer에 등록된 InternalApiKeyInterceptor 담당) */
@Tag(name = "내부 날씨(FastAPI)", description = "X-Internal-Api-Key 헤더로 인증. 침수모델이 입력으로 쓰는 강수·예보")
@RestController
@RequestMapping("/api/internal/weather")
public class WeatherInternalController {

    private final WeatherService weatherService;

    public WeatherInternalController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    // 지하차도 강수: 가장 가까운 날씨 지역의 관측값(weather 테이블)으로 계산한 강수 6개.
    // FastAPI가 이 값을 침수 모델에 넣어 위험점수를 계산한 뒤 POST /api/internal/risk-scores 로 보낸다
    // 지하차도 예보: 가장 가까운 날씨 지역의 앞으로 24시간 시간별 예보. FastAPI 가 위험도 미래 예측(risk-forecasts)에 쓴다
    @Operation(summary = "지하차도 예보", description = "가장 가까운 날씨 지역의 앞으로 24시간 시간별 예보. 침수모델이 위험점수 예측(risk-forecasts)을 계산할 때 쓴다.")
    @GetMapping("/underpasses/{id}/forecast")
    public ApiResponse<List<UnderpassForecastItem>> underpassForecast(@PathVariable("id") Long id) {
        return ApiResponse.ok(weatherService.getUnderpassForecast(id));
    }

    @Operation(summary = "지하차도 강수", description = "가장 가까운 날씨 지역의 관측값으로 계산한 강수 6개(현재, 1·3·6·12·24시간 누적). 1시간 누적은 최근 관측 1시간 전부터 직전까지의 합이다. 침수모델 입력용.")
    @GetMapping("/underpasses/{id}/rainfall")
    public ApiResponse<UnderpassRainfallResponse> underpassRainfall(@PathVariable("id") Long id) {
        return ApiResponse.ok(weatherService.getUnderpassRainfall(id));
    }
}