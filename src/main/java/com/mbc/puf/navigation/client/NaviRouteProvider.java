package com.mbc.puf.navigation.client;

import java.util.List;

import com.mbc.puf.navigation.dto.NaviRoute;

/** 길찾기를 해 주는 곳(지금은 카카오모빌리티). 우회 판단은 DetourService 몫이고 여기는 "경로 한 개 얻기"만 한다 */
public interface NaviRouteProvider {

    /**
     * @param waypoints 경유지 {lat, lng} 목록 (없으면 빈 리스트)
     * @param heading   출발할 때 차가 향하는 방향(도, 북쪽 0 / 동쪽 90). 모르면 null
     */
    NaviRoute route(double oLat, double oLng, double dLat, double dLng, List<double[]> waypoints, Double heading);
}
