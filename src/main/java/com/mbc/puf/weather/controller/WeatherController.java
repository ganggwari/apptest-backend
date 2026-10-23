package com.mbc.puf.weather.controller;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.weather.dto.WeatherResponse;
import com.mbc.puf.weather.service.WeatherService;

/** 고객 웹 날씨 화면(CP-08). 로그인 없이 공개.
 *  위경도(지역 선택·지도 클릭 위치)에서 가장 가까운 날씨 지역의 현재 날씨와 예보를 준다 */
@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    // 예: GET /api/weather?lat=37.5665&lng=126.978
    @GetMapping
    public ApiResponse<WeatherResponse> weather(@RequestParam("lat") BigDecimal lat,
                                                @RequestParam("lng") BigDecimal lng) {
        return ApiResponse.ok(weatherService.getWeather(lat, lng));
    }
}
