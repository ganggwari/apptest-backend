package com.mbc.puf.pm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.pm.dto.PmActionItem;
import com.mbc.puf.pm.dto.PmActionRequest;
import com.mbc.puf.pm.dto.PmEventItem;
import com.mbc.puf.pm.dto.PmEventStatusRequest;
import com.mbc.puf.pm.service.PmEventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 예지보전 점검 처리 (점검 대기 상태 변경, 조치 기록). 관리자 담당 지역만 */
@Tag(name = "예지보전 점검 처리", description = "점검 대기의 상태 변경(확인필요→점검중→조치완료/기타)과 조치 기록 추가")
@RestController
@RequestMapping("/api/pm/events")
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class PmEventController {

    private final PmEventService pmEventService;

    public PmEventController(PmEventService pmEventService) {
        this.pmEventService = pmEventService;
    }

    @Operation(summary = "점검 대기 상태 변경",
            description = "IN_PROGRESS(점검중) / RESOLVED(조치완료) / ETC(기타). 조치완료·기타로 끝낼 때는 content(조치 내용) 필수이고 점검 이력에 남는다. "
                    + "끝난 점검은 다시 열 수 없다(409). 끝나야 같은 설비의 다음 점검 대상·알림이 새로 생긴다.")
    @PatchMapping("/{eventId}/status")
    public ApiResponse<PmEventItem> changeStatus(@AuthenticationPrincipal LoginUser loginUser,
                                                 @PathVariable("eventId") Long eventId,
                                                 @Valid @RequestBody PmEventStatusRequest request) {
        return ApiResponse.ok(pmEventService.changeStatus(loginUser, eventId, request));
    }

    @Operation(summary = "점검 기록 추가", description = "상태는 그대로 두고 조치 내용만 점검 이력에 추가한다")
    @PostMapping("/{eventId}/actions")
    public ResponseEntity<ApiResponse<PmActionItem>> addAction(@AuthenticationPrincipal LoginUser loginUser,
                                                               @PathVariable("eventId") Long eventId,
                                                               @Valid @RequestBody PmActionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(pmEventService.addAction(loginUser, eventId, request)));
    }
}
