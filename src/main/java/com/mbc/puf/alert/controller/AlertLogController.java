package com.mbc.puf.alert.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.alert.dto.AlertLogListItem;
import com.mbc.puf.alert.dto.AlertLogQuery;
import com.mbc.puf.alert.service.AlertLogService;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.request.VehicleQuery;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 알림 발송 기록 조회 (MP-15). 4개 목록 모두 같은 응답 형식(AlertLogListItem)이다.
 * 공통 조건: customerId, customerName(부분일치), channel, status, startDate~endDate, page, size
 * 위험 알림 전용: regionId, underpassId (안 주면 관리자 담당 지역 전체), carNum, alertType, received
 * 공지 알림 전용: noticeId (시설과 무관해서 지역·차량 조건은 무시)
 */
@Tag(name = "알림 발송 기록", description = "위험 알림(접근/최종)·공지 알림의 발송 기록 조회 (관리자)")
@RestController
@RequestMapping("/api/alert-logs")
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class AlertLogController {

    private final AlertLogService alertLogService;

    public AlertLogController(AlertLogService alertLogService) {
        this.alertLogService = alertLogService;
    }

    @Operation(summary = "위험 알림 기록(알림 단위)",
            description = "접근/최종 알림 1건 = 1행. 지역·지하차도를 안 주면 내 담당 지역 전체. channels 에 채널별 결과, pushStatus 에 푸시 수신 요약.")
    @GetMapping
    public ApiResponse<PageResponse<AlertLogListItem>> alerts(
            @AuthenticationPrincipal LoginUser loginUser,
            @ModelAttribute ScopeQuery scope,
            @ModelAttribute AlertLogQuery query,
            @ModelAttribute VehicleQuery vehicle,
            @ModelAttribute PeriodQuery period,
            @ModelAttribute PageQuery page) {
        period.validate();
        return ApiResponse.ok(alertLogService.getAlerts(loginUser, scope, query, vehicle, period, page));
    }

    @Operation(summary = "위험 알림 발송 기록(채널·기기 단위)",
            description = "alert_delivery 1건 = 1행. channel(PUSH_WEB/PUSH_APP/SMS), status(PENDING/SUCCESS/FAIL), platform 으로 거를 수 있다.")
    @GetMapping("/deliveries")
    public ApiResponse<PageResponse<AlertLogListItem>> alertDeliveries(
            @AuthenticationPrincipal LoginUser loginUser,
            @ModelAttribute ScopeQuery scope,
            @ModelAttribute AlertLogQuery query,
            @ModelAttribute VehicleQuery vehicle,
            @ModelAttribute PeriodQuery period,
            @ModelAttribute PageQuery page) {
        period.validate();
        return ApiResponse.ok(alertLogService.getAlertDeliveries(loginUser, scope, query, vehicle, period, page));
    }

    @Operation(summary = "공지 알림 기록(알림 단위)",
            description = "공지 x 고객 1건 = 1행. 공지는 시설과 무관해서 지역 조건이 없다. noticeId 로 한 공지의 발송 대상만 볼 수 있다.")
    @GetMapping("/notices")
    public ApiResponse<PageResponse<AlertLogListItem>> notices(
            @ModelAttribute AlertLogQuery query,
            @ModelAttribute PeriodQuery period,
            @ModelAttribute PageQuery page) {
        period.validate();
        return ApiResponse.ok(alertLogService.getNotices(query, period, page));
    }

    @Operation(summary = "공지 알림 발송 기록(채널·기기 단위)",
            description = "notice_delivery 1건 = 1행.")
    @GetMapping("/notices/deliveries")
    public ApiResponse<PageResponse<AlertLogListItem>> noticeDeliveries(
            @ModelAttribute AlertLogQuery query,
            @ModelAttribute PeriodQuery period,
            @ModelAttribute PageQuery page) {
        period.validate();
        return ApiResponse.ok(alertLogService.getNoticeDeliveries(query, period, page));
    }
}
