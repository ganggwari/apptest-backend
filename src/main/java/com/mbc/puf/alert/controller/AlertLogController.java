package com.mbc.puf.alert.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.alert.dto.AlertLogListItem;
import com.mbc.puf.alert.service.AlertService;
import com.mbc.puf.common.enums.AlertType;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.request.VehicleQuery;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;

/** 알림 발송 이력 조회 (MP-15) */
@RestController
@RequestMapping("/api/alert-logs")
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class AlertLogController {

    private final AlertService alertService;

    public AlertLogController(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * 알림 로그 목록. 전부 선택 조건.
     *  underpassId, regionId : 공통 Scope 조건 (REGIONAL_ADMIN은 본인 담당 지역으로 고정)
     *  customerId            : 그 고객에게 나간 알림만
     *  alertType             : PREDICTIVE/FINAL 중 하나만
     *  carNum                : 공통 Vehicle 조건 (공백 무시 포함검색. 예전 keyword)
     *  startDate~endDate     : 공통 Period 조건 (발송일, endDate 당일 포함)
     *  page, size            : 공통 Page 조건
     */
    @GetMapping
    public ApiResponse<PageResponse<AlertLogListItem>> list(
            @AuthenticationPrincipal LoginUser loginUser,
            @ModelAttribute ScopeQuery scope,
            @RequestParam(name = "customerId", required = false) Long customerId,
            @RequestParam(name = "alertType", required = false) AlertType alertType,
            @ModelAttribute VehicleQuery vehicle,
            @ModelAttribute PeriodQuery period,
            @ModelAttribute PageQuery page) {
        period.validate();
        return ApiResponse.ok(alertService.getAlertLogs(loginUser, scope, customerId, alertType, vehicle, period, page));
    }
}