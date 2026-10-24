package com.mbc.puf.pm.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.enums.PmEventStatus;
import com.mbc.puf.common.enums.PmLevel;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.pm.dto.PmActionItem;
import com.mbc.puf.pm.dto.PmEquipmentDetailResponse;
import com.mbc.puf.pm.dto.PmEquipmentListItem;
import com.mbc.puf.pm.dto.PmEventItem;
import com.mbc.puf.pm.dto.PmScorePoint;
import com.mbc.puf.pm.dto.PmSensorTrendResponse;
import com.mbc.puf.pm.service.PmQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 예지보전 화면 조회 (MP-20: 설비 목록·상세, 점검 대기, 점검 이력). 관리자 담당 지역만 */
@Tag(name = "예지보전", description = "설비 목록·상세·점수 추이, 점검 대기, 점검 이력 조회. 점수는 예지보전 서버(8002)가 보낸 값")
@RestController
@RequestMapping("/api/pm")
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class PmController {

    private final PmQueryService pmQueryService;

    public PmController(PmQueryService pmQueryService) {
        this.pmQueryService = pmQueryService;
    }

    /**
     * 설비 목록. 전부 선택 조건.
     *  regionId, underpassId : 공통 Scope 조건 (담당 지역으로 고정)
     *  type                  : CCTV / DISPLAY / PUMP / WATER_LEVEL
     *  level                 : 최근 단계 SAFE / CAUTION / DANGER
     *  page, size            : 공통 Page 조건. 정렬은 최근 점수 높은 순
     */
    @Operation(summary = "설비 목록", description = "설비마다 최근 점수·단계·주요 사유·7일 변화·마지막 수신·점검 상태")
    @GetMapping("/equipments")
    public ApiResponse<PageResponse<PmEquipmentListItem>> equipments(
            @AuthenticationPrincipal LoginUser loginUser,
            @ModelAttribute ScopeQuery scope,
            @RequestParam(name = "type", required = false) PmEquipmentType type,
            @RequestParam(name = "level", required = false) PmLevel level,
            @ModelAttribute PageQuery page) {
        return ApiResponse.ok(pmQueryService.getEquipments(loginUser, scope, type, level, page));
    }

    @Operation(summary = "설비 상세", description = "목록 정보 + 판단 근거(모델 세부값) + 주의/위험 기준 + 최근 점검일·최근 점검 이력 5건")
    @GetMapping("/equipments/{equipId}")
    public ApiResponse<PmEquipmentDetailResponse> equipment(@AuthenticationPrincipal LoginUser loginUser,
                                                            @PathVariable("equipId") Long equipId) {
        return ApiResponse.ok(pmQueryService.getEquipment(loginUser, equipId));
    }

    @Operation(summary = "설비 위험 점수 추이 (그래프)", description = "최근 days일(1~30, 기본 7)을 bucketMinutes(기본: 7일이면 60분, 1일이면 10분) 구간마다 평균·최고 점수로")
    @GetMapping("/equipments/{equipId}/scores")
    public ApiResponse<List<PmScorePoint>> scores(@AuthenticationPrincipal LoginUser loginUser,
                                                  @PathVariable("equipId") Long equipId,
                                                  @RequestParam(name = "days", defaultValue = "7") int days,
                                                  @RequestParam(name = "bucketMinutes", required = false) Integer bucketMinutes) {
        return ApiResponse.ok(pmQueryService.getScores(loginUser, equipId, days, bucketMinutes));
    }

    @Operation(summary = "원인 센서 시계열", description = "센서 컬럼(column)의 최근 days일(1~30, 기본 7) 1시간 평균 + 평소 범위(처음 28일 평균 ± 2σ). 컬럼 이름은 설비 종류별 센서 컬럼")
    @GetMapping("/equipments/{equipId}/sensor-trend")
    public ApiResponse<PmSensorTrendResponse> sensorTrend(@AuthenticationPrincipal LoginUser loginUser,
                                                          @PathVariable("equipId") Long equipId,
                                                          @RequestParam(name = "column") String column,
                                                          @RequestParam(name = "days", defaultValue = "7") int days) {
        return ApiResponse.ok(pmQueryService.getSensorTrend(loginUser, equipId, column, days));
    }

    /**
     * 점검 대기 (예지보전 이벤트). status 를 안 주면 처리 중(CHECK_NEEDED·IN_PROGRESS)만.
     * 최근 발생 순. 지금 점수(currentScore)도 같이 준다.
     */
    @Operation(summary = "점검 대기", description = "status 생략 시 확인필요·점검중만. CHECK_NEEDED / IN_PROGRESS / RESOLVED / ETC")
    @GetMapping("/events")
    public ApiResponse<PageResponse<PmEventItem>> events(
            @AuthenticationPrincipal LoginUser loginUser,
            @ModelAttribute ScopeQuery scope,
            @RequestParam(name = "status", required = false) PmEventStatus status,
            @RequestParam(name = "type", required = false) PmEquipmentType type,
            @ModelAttribute PageQuery page) {
        return ApiResponse.ok(pmQueryService.getEvents(loginUser, scope, status, type, page));
    }

    /** 점검 이력 (조치 기록). 점검 전(이벤트 점수) → 점검 후(조치 뒤 첫 점수) 점수 포함 */
    @Operation(summary = "점검 이력", description = "조치 기록 최근 순. startDate~endDate 는 조치 기록일 기준(endDate 당일 포함)")
    @GetMapping("/actions")
    public ApiResponse<PageResponse<PmActionItem>> actions(
            @AuthenticationPrincipal LoginUser loginUser,
            @ModelAttribute ScopeQuery scope,
            @RequestParam(name = "type", required = false) PmEquipmentType type,
            @RequestParam(name = "equipId", required = false) Long equipId,
            @ModelAttribute PeriodQuery period,
            @ModelAttribute PageQuery page) {
        period.validate();
        return ApiResponse.ok(pmQueryService.getActions(loginUser, scope, type, equipId, period, page));
    }
}
