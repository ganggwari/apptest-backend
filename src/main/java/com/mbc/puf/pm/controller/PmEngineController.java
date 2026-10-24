package com.mbc.puf.pm.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.pm.dto.PmEngineStatusResponse;
import com.mbc.puf.pm.service.PmEngineService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 예지보전 엔진(예지보전 서버) 상태. 관리자 */
@Tag(name = "예지보전 엔진 상태", description = "예지보전 서버가 점수를 잘 보내고 있는지, 센서 데이터가 끊긴 설비가 있는지")
@RestController
@RequestMapping("/api/pm/engine-status")
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class PmEngineController {

    private final PmEngineService engineService;

    public PmEngineController(PmEngineService engineService) {
        this.engineService = engineService;
    }

    @Operation(summary = "예지보전 엔진 상태",
            description = "status(WAITING/NORMAL/DELAYED/DOWN)는 마지막으로 점수를 받은 뒤 흐른 시간으로 정한다. "
                    + "내 담당 지역 설비의 점수 수신 수·센서 끊김 수·처리 중 미수신 이벤트 수와 종류별 최신 점수·센서 시각을 함께 준다")
    @GetMapping
    public ApiResponse<PmEngineStatusResponse> status(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(engineService.getStatus(loginUser));
    }
}
