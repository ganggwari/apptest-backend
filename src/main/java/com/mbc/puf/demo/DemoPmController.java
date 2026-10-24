package com.mbc.puf.demo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 시연용 예지보전 설비 점수 버튼 API (puf.demo.enabled=true 일 때만) */
@Tag(name = "시연(예지보전)", description = "시연 전용. 설비 위험 점수를 끌어올려 예지보전 이벤트·푸시·SSE 알림을 낸다")
@RestController
@RequestMapping("/api/demo/pm")
@ConditionalOnProperty(name = "puf.demo.enabled", havingValue = "true")
public class DemoPmController {

    private final DemoPmService service;

    public DemoPmController(DemoPmService service) {
        this.service = service;
    }

    public static class BoostRequest {
        private List<Long> equipIds;
        private BigDecimal score;

        public List<Long> getEquipIds() { return equipIds; }
        public void setEquipIds(List<Long> equipIds) { this.equipIds = equipIds; }
        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }
    }

    public static class ReleaseRequest {
        private List<Long> equipIds;

        public List<Long> getEquipIds() { return equipIds; }
        public void setEquipIds(List<Long> equipIds) { this.equipIds = equipIds; }
    }

    @Operation(summary = "설비 목록·최신 점수")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.list());
    }

    @Operation(summary = "설비 점수 올리기(고정)", description = "equipIds 설비들에 score(0~100)를 넣는다. 70 이상이면 위험(긴급 알림), 40~69 는 주의(급상승이면 이벤트).")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    @PostMapping
    public ApiResponse<List<Map<String, Object>>> boost(@RequestBody BoostRequest request) {
        return ApiResponse.ok(service.boost(request.getEquipIds(), request.getScore() == null ? BigDecimal.valueOf(85) : request.getScore()));
    }

    @Operation(summary = "고정 풀기")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    @DeleteMapping
    public ApiResponse<List<Map<String, Object>>> release(@RequestBody(required = false) ReleaseRequest request) {
        return ApiResponse.ok(service.release(request == null ? null : request.getEquipIds()));
    }
}
