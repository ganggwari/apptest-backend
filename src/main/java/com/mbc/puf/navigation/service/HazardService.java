package com.mbc.puf.navigation.service;

import java.util.List;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.navigation.dto.HazardState;

/** 내비가 피해야 할 위험지점(관심 이상이거나 차단 중인 지하차도)을 DB 에서 읽어 관리한다 */
public interface HazardService {

    /** 판정용 위험지점. segments 는 [[입구lat,lng],[출구lat,lng]] 목록(없으면 null) */
    record Hazard(Long id, String name, double lat, double lng, double lengthM, RiskLevel level,
                  boolean blocked, double radiusM, List<double[][]> segments) {
    }

    /** 지금 피해야 할 위험지점 전체 (관심 이상) */
    List<Hazard> activeHazards();

    /** 위험 상태가 바뀔 때마다 올라가는 번호 */
    long version();

    /** 화면에 내려주는 현재 위험 상태 */
    HazardState state();

    /** 2초마다 DB 를 보고 위험 상태가 달라졌으면 알린다 */
    void refreshIfChanged();

    /** 지금 DB 기준으로 다시 읽고, 달라졌으면 WebSocket / SSE 로 알린다 */
    void refresh();
}
