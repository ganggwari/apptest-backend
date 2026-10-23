package com.mbc.puf.member.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.enums.ManagerStatus;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.member.dto.ManagerRegionsRequest;
import com.mbc.puf.member.dto.ManagerShiftRequest;
import com.mbc.puf.member.dto.ManagerSummaryResponse;
import com.mbc.puf.member.dto.ShiftResponse;
import com.mbc.puf.member.service.ManagerAdminService;

import jakarta.validation.Valid;

/**
 * 관리자 계정 관리 (MP-12 회원관리의 관리자 탭).
 * 최고관리자가 없어져 로그인한 관리자 누구나 쓸 수 있다 (누가 쓸 수 있는지는 확인 필요 항목).
 * 가입 승인·거절은 없어졌다: 가입하면 일정 시간 뒤 자동 승인(ManagerAutoApproveScheduler).
 */
@RestController
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class ManagerAdminController {

    private final ManagerAdminService managerAdminService;

    public ManagerAdminController(ManagerAdminService managerAdminService) {
        this.managerAdminService = managerAdminService;
    }

    /**
     * 관리자 목록.
     *  keyword          : 아이디·이름·이메일·전화번호 통합 검색 (일치 점수 순)
     *  status           : 지정하면 그 상태만 (PENDING = 자동 승인 전)
     *  regionId         : 그 지역을 담당하는 관리자만
     *  shiftId          : 그 근무조 소속만
     *  includeWithdrawn : status 를 지정하지 않았을 때 true 면 탈퇴 계정도 포함 (기본은 제외)
     */
    @GetMapping("/api/managers")
    public ApiResponse<PageResponse<ManagerSummaryResponse>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) ManagerStatus status,
            @RequestParam(name = "regionId", required = false) Long regionId,
            @RequestParam(name = "shiftId", required = false) Long shiftId,
            @RequestParam(name = "includeWithdrawn", defaultValue = "false") boolean includeWithdrawn,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(managerAdminService.getManagerList(keyword, status, regionId, shiftId,
                includeWithdrawn, page, size));
    }

    // 담당 지역 배정: 보낸 목록으로 통째로 교체. {"regionIds": [2, 5]}  /  담당 없음은 {"regionIds": []}
    @PutMapping("/api/managers/{id}/regions")
    public ApiResponse<ManagerSummaryResponse> assignRegions(@PathVariable("id") Long id,
                                                             @Valid @RequestBody ManagerRegionsRequest request) {
        return ApiResponse.ok("담당 지역을 변경했습니다.", managerAdminService.assignRegions(id, request.getRegionIds()));
    }

    // 근무조 배정. {"shiftId": 1}  /  근무조 없음은 {"shiftId": null}
    @PutMapping("/api/managers/{id}/shift")
    public ApiResponse<ManagerSummaryResponse> assignShift(@PathVariable("id") Long id,
                                                           @RequestBody ManagerShiftRequest request) {
        return ApiResponse.ok("근무조를 변경했습니다.", managerAdminService.assignShift(id, request.getShiftId()));
    }

    // 강제 탈퇴 (본인 계정 제외)
    @PostMapping("/api/managers/{id}/withdraw")
    public ApiResponse<ManagerSummaryResponse> withdraw(@PathVariable("id") Long id,
                                                        @AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok("탈퇴 처리했습니다.", managerAdminService.withdraw(id, loginUser.id()));
    }

    // 근무조 목록 (배정 드롭다운용)
    @GetMapping("/api/shifts")
    public ApiResponse<List<ShiftResponse>> shifts() {
        return ApiResponse.ok(managerAdminService.getShifts());
    }
}
