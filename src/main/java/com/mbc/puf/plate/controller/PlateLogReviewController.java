package com.mbc.puf.plate.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.enums.RecognitionStatus;
import com.mbc.puf.common.enums.ReviewStatus;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.request.VehicleQuery;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.plate.dto.PlateLogListItem;
import com.mbc.puf.plate.dto.PlateLogReviewRequest;
import com.mbc.puf.plate.dto.PlateLogReviewResponse;
import com.mbc.puf.plate.service.PlateLogReviewService;

import jakarta.validation.Valid;

/** 번호판 인식 기록 조회 + 판별대기(review_status=PENDING) 처리. 판별은 해당 지하차도 담당 지역만 */
@RestController
@RequestMapping("/api/plate-logs")
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class PlateLogReviewController {

    private final PlateLogReviewService plateLogReviewService;

    public PlateLogReviewController(PlateLogReviewService plateLogReviewService) {
        this.plateLogReviewService = plateLogReviewService;
    }

    /**
     * 번호판 인식 기록 목록 (예전 GET /pending 을 ?reviewStatus=PENDING 으로 흡수).
     *  판별대기 목록 = ?reviewStatus=PENDING (마감 임박한 것부터)
     *  underpassId, regionId : 공통 Scope 조건 (REGIONAL_ADMIN은 본인 담당 지역으로 고정)
     *  reviewStatus          : NONE/PENDING/CONFIRMED/AUTO_PROCESSED
     *  recognitionStatus     : SUCCESS/LOW_CONFIDENCE/FAIL/UNMATCHED
     *  carNum                : 공통 Vehicle 조건 (인식값·정정값에서 공백 무시 포함검색)
     *  startDate~endDate     : 공통 Period 조건 (인식일, endDate 당일 포함)
     *  page, size            : 공통 Page 조건
     */
    @GetMapping
    public ApiResponse<PageResponse<PlateLogListItem>> list(
            @AuthenticationPrincipal LoginUser loginUser,
            @ModelAttribute ScopeQuery scope,
            @RequestParam(name = "reviewStatus", required = false) ReviewStatus reviewStatus,
            @RequestParam(name = "recognitionStatus", required = false) RecognitionStatus recognitionStatus,
            @ModelAttribute VehicleQuery vehicle,
            @ModelAttribute PeriodQuery period,
            @ModelAttribute PageQuery page) {
        period.validate();
        return ApiResponse.ok(plateLogReviewService.getPlateLogs(loginUser, scope, reviewStatus, recognitionStatus,
                vehicle, period, page));
    }

    // 수동 판별 (번호판 고쳐서 재매칭). REGIONAL_ADMIN은 본인 담당 지역 것만 가능
    @PutMapping("/{id}/review")
    public ApiResponse<PlateLogReviewResponse> review(@AuthenticationPrincipal LoginUser loginUser,
                                                        @PathVariable("id") Long id,
                                                        @Valid @RequestBody PlateLogReviewRequest request) {
        return ApiResponse.ok("판별이 완료되었습니다.", plateLogReviewService.review(loginUser, id, request));
    }
}
