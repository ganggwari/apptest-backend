package com.mbc.puf.display.controller;

import com.mbc.puf.common.enums.DisplayCategory;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.display.dto.DisplayHistoryItem;
import com.mbc.puf.display.dto.VmsPageItem;
import com.mbc.puf.display.dto.DisplayPageSaveRequest;
import com.mbc.puf.display.dto.DisplayPresetItem;
import com.mbc.puf.display.service.DisplayService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전광판 문구 관리 API
 *  - 프리셋 조회
 *  - 페이지 CRUD (내용 변경 시에만 히스토리 기록)
 */
@RestController
@RequestMapping("/api/display")
public class DisplayController {

    private final DisplayService displayService;

    public DisplayController(DisplayService displayService) {
        this.displayService = displayService;
    }

    // ── 프리셋 ─────────────────────────────────────────────────────

    /**
     * GET /api/display/presets?category=SAFE
     * 전광판 문구 프리셋 목록. category 파라미터를 생략하면 전체.
     */
    @GetMapping("/presets")
    @PreAuthorize("isAuthenticated() and principal.manager")
    public ResponseEntity<ApiResponse<List<DisplayPresetItem>>> getPresets(
            @RequestParam(name = "category", required = false) DisplayCategory category) {
        return ResponseEntity.ok(ApiResponse.ok("전광판 프리셋 목록입니다.", displayService.getPresets(category)));
    }

    // ── 변경 이력 ──────────────────────────────────────────────────

    /**
     * GET /api/display/history
     * 전광판 문구 변경 이력 (자동 표출·수동 수정 모두). 내 담당 지역 지하차도가 원인인 것만.
     */
    @Operation(summary = "전광판 문구 변경 이력",
            description = "최근 순. vmsId(전광판), regionId·underpassId(원인 지하차도), source(AUTO 자동 / MANUAL 수동), managerName(변경한 직원 이름, 부분일치), "
                    + "startDate~endDate(변경 시각 기준, endDate 당일 포함), page·size. 담당 지역 밖은 자동으로 제외된다. 페이지 삭제는 이력에 남지 않는다.")
    @GetMapping("/history")
    @PreAuthorize("isAuthenticated() and principal.manager")
    public ResponseEntity<ApiResponse<PageResponse<DisplayHistoryItem>>> getHistory(
            @AuthenticationPrincipal LoginUser loginUser,
            @ModelAttribute ScopeQuery scope,
            @RequestParam(name = "vmsId", required = false) Long vmsId,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "managerName", required = false) String managerName,
            @ModelAttribute PeriodQuery period,
            @ModelAttribute PageQuery page) {
        period.validate();
        return ResponseEntity.ok(ApiResponse.ok("전광판 문구 변경 이력입니다.",
                displayService.getHistory(loginUser, scope, vmsId, source, managerName, period, page)));
    }

    // ── 페이지 목록 ────────────────────────────────────────────────

    /**
     * GET /api/display/vms/{vmsId}/pages
     * 특정 전광판 설비의 페이지 목록 (page_no 오름차순).
     */
    @GetMapping("/vms/{vmsId}/pages")
    @PreAuthorize("isAuthenticated() and principal.manager")
    public ResponseEntity<ApiResponse<List<VmsPageItem>>> getPages(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("vmsId") Long vmsId) {
        return ResponseEntity.ok(ApiResponse.ok("전광판 페이지 목록입니다.", displayService.getPages(vmsId)));
    }

    // ── 페이지 생성 ────────────────────────────────────────────────

    /**
     * POST /api/display/vms/{vmsId}/pages
     * 페이지 신규 추가 (page_no 자동 부여, 히스토리 기록).
     */
    @PostMapping("/vms/{vmsId}/pages")
    @PreAuthorize("isAuthenticated() and principal.manager")
    public ResponseEntity<ApiResponse<VmsPageItem>> createPage(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("vmsId") Long vmsId,
            @Valid @RequestBody DisplayPageSaveRequest request) {
        VmsPageItem item = displayService.createPage(loginUser, vmsId, request);
        return ResponseEntity.ok(ApiResponse.ok("전광판 페이지가 추가되었습니다.", item));
    }

    // ── 페이지 수정 ────────────────────────────────────────────────

    /**
     * PUT /api/display/vms/{vmsId}/pages/{pageNo}
     * 페이지 문구 수정 (내용 변경 시에만 히스토리 기록).
     */
    @PutMapping("/vms/{vmsId}/pages/{pageNo}")
    @PreAuthorize("isAuthenticated() and principal.manager")
    public ResponseEntity<ApiResponse<VmsPageItem>> updatePage(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("vmsId") Long vmsId,
            @PathVariable("pageNo") Integer pageNo,
            @Valid @RequestBody DisplayPageSaveRequest request) {
        VmsPageItem item = displayService.updatePage(loginUser, vmsId, pageNo, request);
        return ResponseEntity.ok(ApiResponse.ok("전광판 페이지가 수정되었습니다.", item));
    }

    // ── 페이지 삭제 ────────────────────────────────────────────────

    /**
     * DELETE /api/display/vms/{vmsId}/pages/{pageNo}
     * 페이지 삭제 후 뒤 페이지들 page_no 재정렬.
     */
    @DeleteMapping("/vms/{vmsId}/pages/{pageNo}")
    @PreAuthorize("isAuthenticated() and principal.manager")
    public ResponseEntity<ApiResponse<Void>> deletePage(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("vmsId") Long vmsId,
            @PathVariable("pageNo") Integer pageNo) {
        displayService.deletePage(loginUser, vmsId, pageNo);
        return ResponseEntity.ok(ApiResponse.ok("전광판 페이지가 삭제되었습니다.", null));
    }
}