package com.mbc.puf.demo;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 시연 시작 상태로 DB 되돌리기 버튼 API (puf.demo.enabled=true 일 때만) */
@Tag(name = "시연(초기화)", description = "시연 전용. DB 를 시연 시작 상태로 통째로 되돌린다")
@RestController
@RequestMapping("/api/demo/reset")
@ConditionalOnProperty(name = "puf.demo.enabled", havingValue = "true")
public class DemoResetController {

    private final DemoResetService service;

    public DemoResetController(DemoResetService service) {
        this.service = service;
    }

    @Operation(summary = "시연 시작 상태로 초기화", description = "모든 테이블을 지우고 시드(1~6번)를 다시 넣는다. 10초 안팎 걸린다. 끝나면 앱·웹 화면을 새로고침해야 한다.")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    @PostMapping
    public ApiResponse<Map<String, Object>> reset() {
        return ApiResponse.ok(service.reset());
    }
}
