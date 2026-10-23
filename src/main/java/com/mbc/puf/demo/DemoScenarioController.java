package com.mbc.puf.demo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 시연용 호우 시나리오 API. puf.demo.enabled=true 일 때만 존재한다 (운영에서는 꺼 둔다) */
@Tag(name = "시연(호우 시나리오)", description = "시연 전용. puf.demo.enabled=true 일 때만 동작. 선택한 지역에만 호우를 적용한다")
@RestController
@RequestMapping("/api/demo/scenarios")
@ConditionalOnProperty(name = "puf.demo.enabled", havingValue = "true")
public class DemoScenarioController {

    private final DemoScenarioService scenarioService;

    public DemoScenarioController(DemoScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @Operation(summary = "호우 시나리오 시작",
            description = "선택한 지역의 지하차도에만 호우를 적용한다. 호우예보 → 위험도 상승(관리자 알림) → 수위 급상승·자동 차단 "
                    + "(경찰 이관 PDF·고객 알림·내비 우회) → 비 약화·수위 하강 순서로 약 2분 50초 동안 자동 진행된다. "
                    + "이미 진행 중이면 409. 단계가 바뀔 때마다 SSE data-changed(resource=demo-scenario)가 온다.")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    @PostMapping("/heavy-rain")
    public ApiResponse<DemoScenarioDtos.Status> start(@Valid @RequestBody DemoScenarioDtos.StartRequest request) {
        return ApiResponse.ok(scenarioService.start(request.getRegionIds(), request.getSpeed()));
    }

    @Operation(summary = "호우 시나리오 중지", description = "진행 중인 시나리오를 멈추고 평소 값으로 되돌린다. 차단 해제는 직접 해야 한다.")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    @PostMapping("/stop")
    public ApiResponse<DemoScenarioDtos.Status> stop() {
        return ApiResponse.ok(scenarioService.stop());
    }

    @Operation(summary = "호우 시나리오 현재 상태", description = "진행 여부, 현재 단계, 경과 초, 단계 목록. 진행 바 표시용.")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    @GetMapping("/current")
    public ApiResponse<DemoScenarioDtos.Status> current() {
        return ApiResponse.ok(scenarioService.status());
    }
}
