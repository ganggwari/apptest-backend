package com.mbc.puf.underpass.service;

import java.util.List;

import com.mbc.puf.common.enums.TransitStatus;
import com.mbc.puf.common.enums.UnderpassCurrentStatus;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.request.VehicleQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.internal.dto.RiskScoreTrendItem;
import com.mbc.puf.underpass.dto.GateCameraRequest;
import com.mbc.puf.underpass.dto.ControlHistoryItem;
import com.mbc.puf.underpass.dto.TransitHistoryItem;
import com.mbc.puf.underpass.dto.UnderpassCreateRequest;
import com.mbc.puf.underpass.dto.UnderpassDetailResponse;
import com.mbc.puf.underpass.dto.UnderpassListItem;
import com.mbc.puf.underpass.dto.UnderpassUpdateRequest;

public interface UnderpassService {

    /** 지하차도 목록 정렬 기준 */
    enum Sort {
        NAME,        // 이름 가나다순 (기본)
        RISK_SCORE,  // 최신 위험점수 높은 순 (점수 없으면 맨 뒤)
        LATEST       // 최근 등록순
    }

    /** 지하차도 목록 범위 */
    enum ListScope {
        ALL,   // 전체 (기본, 로그인 없이 공개). 지도 마커용
        MINE   // 내 담당 지역만 (관리자 전용). 관리자 시설 목록용
    }

    // 지하차도 목록 (공개). regionId·keyword(이름)·status 는 선택. scope=MINE 이면 로그인한 관리자의 담당 지역만.
    // loginUser 는 비로그인이면 null (각 항목의 manageable 계산에만 쓴다)
    PageResponse<UnderpassListItem> getUnderpassList(LoginUser loginUser, ListScope scope, Long regionId,
                                                     String keyword, UnderpassCurrentStatus status,
                                                     Sort sort, PageQuery page);

    // 지하차도 상세 (공개). loginUser 는 비로그인이면 null (manageable 계산에만 쓴다)
    UnderpassDetailResponse getUnderpass(LoginUser loginUser, Long id);

    // 위험도 추이 (그래프용 시계열이라 List, 시간 오름차순)
    List<RiskScoreTrendItem> getRiskScoreTrend(Long id, Integer limit, Integer hours);

    // 위험도 미래 예측 (그래프의 미래 구간, 시간 오름차순). 지금 이후 시점만
    List<com.mbc.puf.internal.dto.RiskForecastItem> getRiskForecast(Long id);

    UnderpassDetailResponse createUnderpass(LoginUser loginUser, UnderpassCreateRequest request);

    UnderpassDetailResponse updateUnderpass(LoginUser loginUser, Long id, UnderpassUpdateRequest request);

    void deleteUnderpass(LoginUser loginUser, Long id);

    // 차단/해제는 control 도메인(POST /api/underpasses/{id}/block, /release)에서만 한다

    // 상행/하행 입·출구 카메라 4자리 한 번에 지정 (null = 지정 해제)
    UnderpassDetailResponse assignGateCameras(LoginUser loginUser, Long id, GateCameraRequest request);

    // 통과 기록 목록 (잔류차량 목록 = status=REMAINING&underpassId=..). Scope/Vehicle/Period/Page 공통 조건
    PageResponse<TransitHistoryItem> getTransitHistory(LoginUser loginUser, ScopeQuery scope, TransitStatus status,
                                                       VehicleQuery vehicle, PeriodQuery period, PageQuery page);

    // 제어이력 (이벤트 + 전광판 문구 변경, 최신순). kind: EVENT/DISPLAY/null(전체)
    PageResponse<ControlHistoryItem> getControlHistory(LoginUser loginUser, Long id, String kind, PeriodQuery period, PageQuery page);

    // 잔류/장시간 IN_TRANSIT 자동 종료 (스케줄러 전용). 처리된 행 수
    // 통과 기록 1건을 관리자가 수동으로 통과 완료(EXITED) 처리. 이미 완료면 409, 담당 지역 밖이면 403
    TransitHistoryItem completeTransit(LoginUser loginUser, Long transitId);
}