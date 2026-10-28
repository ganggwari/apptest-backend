package com.mbc.puf.handover.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.handover.dto.HandoverCreateRequest;
import com.mbc.puf.handover.dto.HandoverDetail;
import com.mbc.puf.handover.dto.HandoverListItem;
import com.mbc.puf.handover.dto.HandoverMemoRequest;
import com.mbc.puf.handover.service.HandoverService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 근무조 인수인계 리포트. 관리자 */
@Tag(name = "근무조 인수인계", description = "교대 때 위험도·예지보전 자동 집계 + 수기 메모. 로그인 직후 팝업은 /previous")
@RestController
@RequestMapping("/api/shift-handovers")
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class HandoverController {

    private final HandoverService handoverService;

    public HandoverController(HandoverService handoverService) {
        this.handoverService = handoverService;
    }

    @Operation(summary = "직전 근무조 인수인계 (로그인 직후 팝업)",
            description = "시연용 팝업. 로그인 관리자의 직전 근무조 중 담당 지역이 겹치는 실제 근무자 이름을 조회하고, 예시 업무 요약과 조회 시점의 실제 PM 미처리 이벤트를 반환한다. "
                    + "summary.demo=true, pmLive=true. counts의 block/riskUp/pmChecked는 예시, pmPending은 실제 전체 건수. "
                    + "summary.pm은 최근 8시간 및 현재 미처리 현황이다. 복수 이전 근무자는 authorName에 함께 표시하며 미배정이면 명시한다. 저장하지 않는다.")
    @GetMapping("/previous")
    public ApiResponse<HandoverDetail> previous(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(handoverService.getPrevious(loginUser));
    }

    @Operation(summary = "작성 전 미리보기", description = "내 근무조(또는 shiftId)의 지금 근무를 집계만 하고 저장하지 않는다. 근무조가 없으면 shiftId 필수")
    @GetMapping("/preview")
    public ApiResponse<HandoverDetail> preview(@AuthenticationPrincipal LoginUser loginUser,
                                               @RequestParam(name = "shiftId", required = false) Long shiftId) {
        return ApiResponse.ok(handoverService.preview(loginUser, shiftId));
    }

    @Operation(summary = "인수인계 작성", description = "지금 시점의 자동 집계(통제·위험 단계 변화·예지보전, 처리 중 점검과 차단·위험 지하차도 이월)를 스냅샷으로 저장하고 수기 메모를 붙인다")
    @PostMapping
    public ResponseEntity<ApiResponse<HandoverDetail>> create(@AuthenticationPrincipal LoginUser loginUser,
                                                              @Valid @RequestBody HandoverCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(handoverService.create(loginUser, request)));
    }

    @Operation(summary = "인수인계 목록", description = "근무일(startDate~endDate) 최근 순. shiftId 로 근무조, managerName 으로 작성자 이름(부분일치) 필터")
    @GetMapping
    public ApiResponse<PageResponse<HandoverListItem>> list(
            @RequestParam(name = "shiftId", required = false) Long shiftId,
            @RequestParam(name = "managerName", required = false) String managerName,
            @ModelAttribute PeriodQuery period,
            @ModelAttribute PageQuery page) {
        return ApiResponse.ok(handoverService.list(shiftId, managerName, period, page));
    }

    @Operation(summary = "인수인계 상세", description = "저장된 집계 스냅샷 + 메모")
    @GetMapping("/{id}")
    public ApiResponse<HandoverDetail> get(@PathVariable("id") Long id) {
        return ApiResponse.ok(handoverService.get(id));
    }

    @Operation(summary = "인수인계 메모 수정", description = "작성한 사람만")
    @PutMapping("/{id}/memo")
    public ApiResponse<HandoverDetail> updateMemo(@AuthenticationPrincipal LoginUser loginUser,
                                                  @PathVariable("id") Long id,
                                                  @Valid @RequestBody HandoverMemoRequest request) {
        return ApiResponse.ok(handoverService.updateMemo(loginUser, id, request));
    }
}
