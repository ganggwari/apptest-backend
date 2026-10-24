package com.mbc.puf.pm.service;

import java.util.List;

import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.enums.PmEventStatus;
import com.mbc.puf.common.enums.PmLevel;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.pm.dto.PmActionItem;
import com.mbc.puf.pm.dto.PmEquipmentDetailResponse;
import com.mbc.puf.pm.dto.PmEquipmentListItem;
import com.mbc.puf.pm.dto.PmEventItem;
import com.mbc.puf.pm.dto.PmScorePoint;
import com.mbc.puf.pm.dto.PmSensorTrendResponse;

/** 예지보전 화면 조회 (설비 목록·상세·점수 추이·점검 대기·점검 이력). 관리자 담당 지역만 */
public interface PmQueryService {

    PageResponse<PmEquipmentListItem> getEquipments(LoginUser loginUser, ScopeQuery scope, PmEquipmentType type,
                                                    PmLevel level, PageQuery page);

    PmEquipmentDetailResponse getEquipment(LoginUser loginUser, Long equipId);

    PmSensorTrendResponse getSensorTrend(LoginUser loginUser, Long equipId, String column, int days);

    List<PmScorePoint> getScores(LoginUser loginUser, Long equipId, int days, Integer bucketMinutes);

    PageResponse<PmEventItem> getEvents(LoginUser loginUser, ScopeQuery scope, PmEventStatus status, PmEquipmentType type,
                                        PageQuery page);

    PageResponse<PmActionItem> getActions(LoginUser loginUser, ScopeQuery scope, PmEquipmentType type, Long equipId,
                                          PeriodQuery period, PageQuery page);
}
