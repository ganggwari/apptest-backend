package com.mbc.puf.alert.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.alert.dto.MyAlertItem;
import com.mbc.puf.alert.service.AlertService;
import com.mbc.puf.common.enums.AlertType;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;

/**
 * 고객이 자기 앞으로 온 알림(재난문자 형식)을 조회하는 화면용.
 * 회원 정보 DB를 "전국민 DB"로 본다는 설정이라, 번호판이 매칭된 고객이면 별도 가입 절차 없이도
 * 이 목록을 보게 되는 그림 — 매칭 로직(AlertServiceImpl.sendPredictiveAlert/sendRoiFinalAlert) 자체는
 * 그대로 두고, 이미 쌓인 alert_log를 고객 본인 것만 문자 형식으로 내려주는 조회 전용 엔드포인트.
 */
@RestController
@RequestMapping("/api/customers/me/alerts")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerAlertController {

    private final AlertService alertService;

    public CustomerAlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * 내 알림 목록 (최신순, 문자 화면용 — 지하차도 이름/수신번호/본문 문구까지 조합해서 내려줌).
     * 프런트 폰 목업 화면이 주기적으로 폴링해서, 마지막으로 본 id보다 새 항목이 있으면
     * "문자 온 것처럼" 배너/애니메이션을 띄우는 용도.
     *  alertType : PREDICTIVE(접근 예측)/FINAL(진입 직전) 중 하나만 보고 싶을 때 선택 필터
     *  startDate~endDate : 공통 Period 조건 (발송일, endDate 당일 포함), page/size : 공통 Page 조건
     */
    @GetMapping
    public ApiResponse<PageResponse<MyAlertItem>> myAlerts(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(name = "alertType", required = false) AlertType alertType,
            @ModelAttribute PeriodQuery period,
            @ModelAttribute PageQuery page) {
        period.validate();
        return ApiResponse.ok(alertService.getMyAlerts(loginUser.id(), alertType, period, page));
    }
}
