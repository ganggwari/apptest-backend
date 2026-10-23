package com.mbc.puf.navigation.service;

import com.mbc.puf.navigation.dto.RouteResult;

/** 위험 지하차도를 피하는 길찾기 */
public interface DetourService {

    /**
     * @param heading 달리는 도중 재탐색할 때 현재 진행방향(도, 0~360). 모르면 null
     */
    RouteResult find(double oLat, double oLng, double dLat, double dLng, Double heading);
}
