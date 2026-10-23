package com.mbc.puf.navigation.service;

import java.math.BigDecimal;
import java.util.List;

import com.mbc.puf.navigation.dto.NavigationPlaceResponse;
import com.mbc.puf.weather.dto.WeatherResponse;

import tools.jackson.databind.JsonNode;

/** 내비 화면의 장소 검색·후보 경로·도착지 날씨 (누구나 쓰는 조회라 IP 별 호출 횟수를 제한한다) */
public interface NavigationSearchService {

    /** 장소 검색. 검색어는 1~50자. 한도를 넘으면 429 */
    List<NavigationPlaceResponse> places(String query, String clientKey);

    /**
     * 후보 경로. origin/destination/waypoint 는 "경도,위도".
     * 경로가 하나도 없으면 404, 입력이 잘못되면 400, 한도를 넘으면 429
     */
    List<JsonNode> directions(String origin, String destination, String waypoint, String clientKey);

    /** 위경도 근처의 현재 날씨 (/api/weather 와 같은 내용) */
    WeatherResponse weather(BigDecimal lat, BigDecimal lng);
}
