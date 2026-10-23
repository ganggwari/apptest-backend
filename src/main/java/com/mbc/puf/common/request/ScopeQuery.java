package com.mbc.puf.common.request;

import java.util.List;

/**
 * [공통 조건 묶음 3: Scope] 시설과 연결된 목록 공통.  ?regionId=2&underpassId=17
 * 요청으로는 regionId 하나만 받는다. ScopeResolver 가 관리자의 담당 지역(manager_region)과 맞춰서
 * 실제로 걸 지역 목록(regionIds)을 채운다.
 *  - regionIds == null : 지역 조건 없음 (전국)
 *  - regionIds 가 있음  : region_id IN (regionIds). 담당 지역이 하나도 없으면 결과 0건이 되도록 [-1]
 */
public class ScopeQuery {

    private Long regionId;
    private Long underpassId;
    private List<Long> regionIds;

    public ScopeQuery() {
    }

    public static ScopeQuery of(Long regionId, Long underpassId) {
        ScopeQuery q = new ScopeQuery();
        q.regionId = regionId;
        q.underpassId = underpassId;
        return q;
    }

    public static ScopeQuery resolved(List<Long> regionIds, Long underpassId) {
        ScopeQuery q = new ScopeQuery();
        q.regionIds = regionIds;
        q.underpassId = underpassId;
        return q;
    }

    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }
    public Long getUnderpassId() { return underpassId; }
    public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }
    public List<Long> getRegionIds() { return regionIds; }
}
