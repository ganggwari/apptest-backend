package com.mbc.puf.pm.controller;

import java.util.List;

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

import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.pm.dto.PmThresholdDetailResponse;
import com.mbc.puf.pm.dto.PmThresholdItem;
import com.mbc.puf.pm.dto.PmThresholdUpdateRequest;
import com.mbc.puf.pm.service.PmThresholdService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 예지보전 기준 설정 (안전/주의/위험 단계가 시작되는 점수). 관리자 담당 지역만 */
@Tag(name = "예지보전 기준 설정", description = "설비별 주의·위험 기준 점수 조회와 수정, 변경 이력")
@RestController
@RequestMapping("/api/pm")
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class PmThresholdController {

    private final PmThresholdService pmThresholdService;

    public PmThresholdController(PmThresholdService pmThresholdService) {
        this.pmThresholdService = pmThresholdService;
    }

    @Operation(summary = "단계 기준 목록", description = "설비마다 주의·위험이 시작되는 점수와 마지막 수정 정보. regionId·underpassId(담당 지역으로 고정), type 필터, 페이지")
    @GetMapping("/thresholds")
    public ApiResponse<PageResponse<PmThresholdItem>> thresholds(
            @AuthenticationPrincipal LoginUser loginUser,
            @ModelAttribute ScopeQuery scope,
            @RequestParam(name = "type", required = false) PmEquipmentType type,
            @ModelAttribute PageQuery page) {
        return ApiResponse.ok(pmThresholdService.getThresholds(loginUser, scope, type, page));
    }

    @Operation(summary = "설비 단계 기준 상세", description = "현재 기준 + 최근 변경 이력 20건(누가·언제·몇 점에서 몇 점으로)")
    @GetMapping("/equipments/{equipId}/thresholds")
    public ApiResponse<PmThresholdDetailResponse> threshold(@AuthenticationPrincipal LoginUser loginUser,
                                                            @PathVariable("equipId") Long equipId) {
        return ApiResponse.ok(pmThresholdService.getThreshold(loginUser, equipId));
    }

    @Operation(summary = "단계 기준 수정", description = "equipIds 로 고른 설비 전부에 같은 주의(cautionMin)·위험(dangerMin) 기준을 적용한다. "
            + "0 < 주의 < 위험 <= 100, 안전은 0 고정. 하나라도 담당 지역 밖이면 전체 거절(403). "
            + "바뀐 설비의 최근 점수 단계는 새 기준으로 다시 판정되고, 이미 만들어진 점검 대기는 그대로다.")
    @PutMapping("/thresholds")
    public ApiResponse<List<PmThresholdItem>> update(@AuthenticationPrincipal LoginUser loginUser,
                                                     @Valid @RequestBody PmThresholdUpdateRequest request) {
        return ApiResponse.ok(pmThresholdService.update(loginUser, request));
    }
}
