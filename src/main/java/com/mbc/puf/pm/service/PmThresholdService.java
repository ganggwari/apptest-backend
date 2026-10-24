package com.mbc.puf.pm.service;

import java.util.List;

import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.pm.dto.PmThresholdDetailResponse;
import com.mbc.puf.pm.dto.PmThresholdItem;
import com.mbc.puf.pm.dto.PmThresholdUpdateRequest;

/** 예지보전 단계 기준(주의·위험이 시작되는 점수) 조회·수정. 관리자 담당 지역만 */
public interface PmThresholdService {

    PageResponse<PmThresholdItem> getThresholds(LoginUser loginUser, ScopeQuery scope, PmEquipmentType type, PageQuery page);

    PmThresholdDetailResponse getThreshold(LoginUser loginUser, Long equipId);

    /** 고른 설비 전부에 같은 기준을 적용한다 (하나라도 담당 지역 밖이면 전체 거절). 바뀐 설비의 최근 점수 단계도 다시 판정한다 */
    List<PmThresholdItem> update(LoginUser loginUser, PmThresholdUpdateRequest request);
}
