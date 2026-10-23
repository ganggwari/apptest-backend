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

import com.mbc.puf.board.dto.FaqCreateRequest;
import com.mbc.puf.board.dto.FaqDetailResponse;
import com.mbc.puf.board.dto.FaqListItem;
import com.mbc.puf.board.dto.FaqOrderRequest;
import com.mbc.puf.board.dto.FaqUpdateRequest;
import com.mbc.puf.board.service.FaqService;
import com.mbc.puf.common.enums.FaqCategory;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.mbc.puf.common.util.ViewCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Tag(name = "FAQ", description = "FAQ 목록·상세는 공개, 등록·수정·삭제는 관리자")
@RestController
@RequestMapping("/api/faqs")
public class FaqController {

    private final FaqService faqService;

    public FaqController(FaqService faqService) {
        this.faqService = faqService;
    }

    // FAQ 목록 (로그인 불필요). keyword: 질문+답변 통합 검색 (없으면 전체)
    //  category : USAGE(이용 방법)/NOTIFICATION(알림·푸시)/VEHICLE(차량·번호판)/ACCOUNT(계정)/ETC(기타), 안 보내면 전체
    @Operation(summary = "FAQ 목록", description = "category 로 분류 필터, keyword 로 질문+답변 검색")
    @GetMapping
    public ApiResponse<PageResponse<FaqListItem>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "category", required = false) FaqCategory category,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(faqService.getFaqList(keyword, category, page, size));
    }

    // 자주 보는 질문 (로그인 불필요): 조회수 높은 순 상위 limit 개 (기본 5, 최대 20)
    @Operation(summary = "자주 보는 질문", description = "조회수 높은 순 상위 N개")
    @GetMapping("/popular")
    public ApiResponse<java.util.List<FaqListItem>> popular(
            @RequestParam(name = "limit", defaultValue = "5") int limit) {
        return ApiResponse.ok(faqService.getPopularFaqs(limit));
    }

    // FAQ 상세 (로그인 불필요). 같은 브라우저가 24시간 안에 다시 열면 조회수는 올리지 않는다(쿠키)
    @Operation(summary = "FAQ 상세", description = "처음 보는 글이면 조회수 +1 (viewed_faq 쿠키로 중복 방지)")
    @GetMapping("/{id}")
    public ApiResponse<FaqDetailResponse> detail(@PathVariable("id") Long id,
                                                 HttpServletRequest request, HttpServletResponse response) {
        boolean countView = ViewCookie.firstView(request, response, "viewed_faq", id);
        return ApiResponse.ok(faqService.getFaq(id, countView));
    }

    // FAQ 등록 (201 Created) - 관리자 전용
    @PostMapping
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ResponseEntity<ApiResponse<FaqDetailResponse>> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody FaqCreateRequest request) {
        FaqDetailResponse created = faqService.createFaq(loginUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("FAQ가 등록되었습니다.", created));
    }

    // FAQ 순서 변경 - 관리자 전용. 원하는 순서대로 id 를 보낸다 (분류 탭 안에서 일부만 보내도 된다)
    @Operation(summary = "FAQ 순서 변경", description = "ids 에 원하는 순서대로 FAQ id 를 보낸다. 보낸 FAQ 들이 가진 순서 번호를 그 순서대로 다시 나눠 가진다")
    @PutMapping("/order")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ApiResponse<Void> reorder(@Valid @RequestBody FaqOrderRequest request) {
        faqService.reorderFaqs(request);
        return ApiResponse.ok("FAQ 순서가 변경되었습니다.", null);
    }

    // FAQ 수정 - 관리자 전용
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ApiResponse<FaqDetailResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody FaqUpdateRequest request) {
        return ApiResponse.ok("FAQ가 수정되었습니다.", faqService.updateFaq(id, request));
    }

    // FAQ 삭제 (소프트 삭제) - 관리자 전용
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        faqService.deleteFaq(id);
        return ApiResponse.ok("FAQ가 삭제되었습니다.", null);
    }
}