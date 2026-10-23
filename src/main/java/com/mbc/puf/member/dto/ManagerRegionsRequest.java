package com.mbc.puf.member.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** 담당 지역 배정 (MP-12). 보낸 목록으로 통째로 바꾼다. 빈 목록이면 담당 지역 없음 */
public class ManagerRegionsRequest {

    @NotNull(message = "regionIds 는 필수입니다. (담당 지역을 모두 빼려면 빈 목록 [])")
    private List<Long> regionIds;

    public ManagerRegionsRequest() {
    }

    public List<Long> getRegionIds() { return regionIds; }
    public void setRegionIds(List<Long> regionIds) { this.regionIds = regionIds; }
}
