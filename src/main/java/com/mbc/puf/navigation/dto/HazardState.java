package com.mbc.puf.navigation.dto;

import java.util.List;

/**
 * 내비가 보는 위험 상태. version 이 바뀌면 "상태가 바뀌었다"는 뜻이라 프런트는 이 값으로 중복 처리를 막는다
 * (서버가 다시 떠도 줄어들지 않도록 시각 기반으로 만든다).
 * active=true 이면 관심 이상인 지하차도가 하나라도 있다.
 */
public record HazardState(long version, boolean active, List<HazardView> hazards) {

    /**
     * @param underpassId 지하차도 id
     * @param level       위험 단계 이름(SAFE 제외: WATCH/CAUTION/ALERT/SEVERE)
     * @param levelLabel  화면 표시용 한글(관심/주의/경계/심각)
     * @param blocked     차단(경계 이상 또는 차단 상태) = 필수 우회 대상. false 면 우회 권유
     * @param radiusM     판정 반경(m)
     * @param lengthM     지하차도 길이(m), 모르면 null
     */
    public record HazardView(Long underpassId, String name, double lat, double lng, String level, String levelLabel,
                             boolean blocked, double radiusM, Double lengthM, String message) {
    }
}
