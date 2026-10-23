package com.mbc.puf.threshold.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.threshold.dto.ThresholdResponse;
import com.mbc.puf.threshold.dto.ThresholdUpdateRequest;
import com.mbc.puf.threshold.service.ThresholdService;

import jakarta.validation.Valid;

/** 지하차도별 위험도 임계치 관리 (MP-19). 조회·조정 모두 관리자 전체 가능하되,
 *  REGIONAL_ADMIN의 조정은 본인 담당 지역 지하차도로만 서비스 레이어에서 제한한다 */
@RestController
@RequestMapping("/api/underpasses/{underpassId}/thresholds")
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class ThresholdController {

    private final ThresholdService thresholdService;

    public ThresholdController(ThresholdService thresholdService) {
        this.thresholdService = thresholdService;
    }

    // 임계치 3행 목록 (관리자면 지역 상관없이 조회 가능)
    @GetMapping
    public ApiResponse<List<ThresholdResponse>> list(@AuthenticationPrincipal LoginUser loginUser,
                                                       @PathVariable("underpassId") Long underpassId) {
        return ApiResponse.ok(thresholdService.getThresholds(loginUser, underpassId));
    }

    // 특정 단계 임계치 조정. 담당 지역(여러 개 가능)의 지하차도만 (서비스에서 체크)
    @PutMapping("/{level}")
    public ApiResponse<ThresholdResponse> update(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("underpassId") Long underpassId,
            @PathVariable("level") RiskLevel level,
            @Valid @RequestBody ThresholdUpdateRequest request) {
        return ApiResponse.ok("임계치가 수정되었습니다.",
                thresholdService.updateThreshold(loginUser, underpassId, level, request));
    }
}