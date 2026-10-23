package com.mbc.puf.navigation.dto;

/**
 * 장소 검색 결과 한 건 (출발지·도착지 고르기용).
 *
 * @param id      카카오 장소 번호 (주소 검색 결과는 null)
 * @param name    장소 이름 (주소 검색이면 주소)
 * @param address 도로명 주소, 없으면 지번 주소
 * @param lat     위도
 * @param lng     경도
 */
public record NavigationPlaceResponse(String id, String name, String address, double lat, double lng) {
}
