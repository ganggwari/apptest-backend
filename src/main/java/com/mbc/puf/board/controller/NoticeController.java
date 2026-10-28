package com.mbc.puf.board.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.board.dto.NoticeCreateRequest;
import com.mbc.puf.board.dto.NoticeDetailResponse;
import com.mbc.puf.board.dto.NoticeListItem;
import com.mbc.puf.board.dto.NoticeUpdateRequest;
import com.mbc.puf.board.service.NoticeService;
import com.mbc.puf.common.enums.NoticeCategory;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.mbc.puf.common.util.ViewCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Tag(name = "공지사항", description = "공지 목록·상세는 공개, 등록·수정·삭제는 관리자")
@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /**
     * 공지사항 목록 (로그인 불필요).
     *  keyword   : 제목+내용 통합 검색 (없으면 전체)
     *  category  : INFO(안내)/MAINTENANCE(점검)/UPDATE(업데이트)/URGENT(긴급), 안 보내면 전체
     *  important : true/false 로 필터, 안 보내면 전체
     */
    @Operation(summary = "공지 목록", description = "category 로 분류 필터, keyword 로 제목+내용 검색")
    @GetMapping
    public ApiResponse<PageResponse<NoticeListItem>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "authorName", required = false) String authorName,
            @RequestParam(name = "category", required = false) NoticeCategory category,
            @RequestParam(name = "important", required = false) Boolean important,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(noticeService.getNoticeList(keyword, authorName, category, important, page, size));
    }

    // 공지사항 상세 (로그인 불필요). 같은 브라우저가 24시간 안에 다시 열면 조회수는 올리지 않는다(쿠키)
    @Operation(summary = "공지 상세", description = "처음 보는 글이면 조회수 +1 (viewed_notice 쿠키로 중복 방지)")
    @GetMapping("/{id}")
    public ApiResponse<NoticeDetailResponse> detail(@PathVariable("id") Long id,
                                                    HttpServletRequest request, HttpServletResponse response) {
        boolean countView = ViewCookie.firstView(request, response, "viewed_notice", id);
        return ApiResponse.ok(noticeService.getNotice(id, countView));
    }

    // 공지사항 등록 (201 Created) - 관리자 전용
    @PostMapping
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ResponseEntity<ApiResponse<NoticeDetailResponse>> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody NoticeCreateRequest request) {
        NoticeDetailResponse created = noticeService.createNotice(loginUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("공지사항이 등록되었습니다.", created));
    }

    // 공지사항 수정 - 관리자 전용
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ApiResponse<NoticeDetailResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody NoticeUpdateRequest request) {
        return ApiResponse.ok("공지사항이 수정되었습니다.", noticeService.updateNotice(id, request));
    }

    // 공지사항 삭제 (소프트 삭제) - 관리자 전용
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        noticeService.deleteNotice(id);
        return ApiResponse.ok("공지사항이 삭제되었습니다.", null);
    }
}