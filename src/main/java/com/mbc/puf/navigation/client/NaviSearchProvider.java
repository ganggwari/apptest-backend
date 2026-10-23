package com.mbc.puf.navigation.client;

import java.util.List;

import com.mbc.puf.navigation.dto.NavigationPlaceResponse;

import tools.jackson.databind.JsonNode;

/** 장소 검색과 "후보 경로 여러 개" 조회를 해 주는 곳(지금은 카카오). 우회 판단과는 상관없는 화면용 조회 */
public interface NaviSearchProvider {

    /** 키워드로 장소를 찾고, 없으면 주소로 다시 찾는다. 부산 시청 근처를 기준으로 가까운 순 */
    List<NavigationPlaceResponse> searchPlaces(String query);

    /**
     * 자동차 길찾기 후보 경로들. 카카오 응답의 routes 중 성공(result_code=0)인 것만 그대로 돌려준다.
     *
     * @param origin      "경도,위도"
     * @param destination "경도,위도"
     * @param waypoint    "경도,위도" (없으면 null). 경유지가 있으면 카카오가 후보 경로를 하나만 준다
     */
    List<JsonNode> directions(String origin, String destination, String waypoint);
}
