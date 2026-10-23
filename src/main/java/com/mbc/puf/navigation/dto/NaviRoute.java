package com.mbc.puf.navigation.dto;

import java.util.List;

/**
 * 길찾기 업체(카카오) 응답을 쓰기 좋게 줄인 것.
 *
 * @param distance 총 거리(m)
 * @param duration 예상 소요(초)
 * @param path     경로를 이루는 점들. 한 점은 {lat, lng}
 * @param guides   회전 안내 목록
 */
public record NaviRoute(int distance, int duration, List<double[]> path, List<Guide> guides) {

    /**
     * 회전 안내 한 건.
     *
     * @param at 출발점부터 이 안내 지점까지 경로를 따라 간 거리(m) - 차가 얼마나 왔는지와 비교하는 용도
     */
    public record Guide(double lat, double lng, String name, String text, int type, int at) {
    }
}
