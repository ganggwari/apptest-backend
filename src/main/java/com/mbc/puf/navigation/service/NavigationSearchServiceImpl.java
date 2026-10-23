package com.mbc.puf.navigation.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.navigation.client.NaviSearchProvider;
import com.mbc.puf.navigation.dto.NavigationPlaceResponse;
import com.mbc.puf.weather.dto.WeatherResponse;
import com.mbc.puf.weather.service.WeatherService;

import tools.jackson.databind.JsonNode;

@Service
public class NavigationSearchServiceImpl implements NavigationSearchService {

    private final NaviSearchProvider provider;
    private final WeatherService weatherService;
    private final int limitPerMinute;

    /** 호출한 쪽(IP) -> {분 단위 시각, 그 분의 호출 수}. 한 분이 지나면 새로 센다 */
    private final Map<String, long[]> counters = new ConcurrentHashMap<>();

    public NavigationSearchServiceImpl(NaviSearchProvider provider, WeatherService weatherService,
                                       @Value("${puf.navigation.search-limit-per-minute:60}") int limitPerMinute) {
        this.provider = provider;
        this.weatherService = weatherService;
        this.limitPerMinute = limitPerMinute;
    }

    @Override
    public List<NavigationPlaceResponse> places(String query, String clientKey) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty() || q.length() > 50) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "검색어는 1~50자로 입력해 주세요.");
        }
        checkLimit(clientKey);
        return provider.searchPlaces(q);
    }

    @Override
    public List<JsonNode> directions(String origin, String destination, String waypoint, String clientKey) {
        checkLngLat(origin, "origin");
        checkLngLat(destination, "destination");
        String wp = waypoint == null || waypoint.isBlank() ? null : waypoint.trim();
        if (wp != null) {
            checkLngLat(wp, "waypoint");
        }
        checkLimit(clientKey);
        List<JsonNode> routes = provider.directions(origin.trim(), destination.trim(), wp);
        if (routes.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "이 구간에서 자동차 경로를 찾지 못했습니다.");
        }
        return routes;
    }

    @Override
    public WeatherResponse weather(BigDecimal lat, BigDecimal lng) {
        return weatherService.getWeather(lat, lng);
    }

    /** "경도,위도" 형식과 범위를 확인한다 */
    private static void checkLngLat(String text, String name) {
        try {
            String[] p = text == null ? new String[0] : text.split(",");
            if (p.length != 2) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, name + " 은(는) '경도,위도' 형식이어야 합니다.");
            }
            double lng = Double.parseDouble(p[0].trim());
            double lat = Double.parseDouble(p[1].trim());
            if (lng < -180 || lng > 180 || lat < -90 || lat > 90) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, name + " 의 좌표 범위가 올바르지 않습니다.");
            }
        } catch (NumberFormatException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, name + " 에 숫자가 아닌 값이 있습니다: " + text);
        }
    }

    private void checkLimit(String clientKey) {
        long minute = System.currentTimeMillis() / 60_000;
        long[] c = counters.compute(clientKey == null ? "-" : clientKey, (k, v) -> {
            if (v == null || v[0] != minute) {
                return new long[]{minute, 1};
            }
            v[1]++;
            return v;
        });
        if (c[1] > limitPerMinute) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(e -> e.getValue()[0] != minute);   // 지난 분 기록 정리
        }
    }
}
