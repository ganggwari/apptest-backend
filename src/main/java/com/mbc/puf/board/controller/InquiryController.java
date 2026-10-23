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

import com.mbc.puf.board.dto.InquiryCreateRequest;
import com.mbc.puf.board.dto.InquiryDetailResponse;
import com.mbc.puf.board.dto.InquiryListItem;
import com.mbc.puf.board.dto.InquiryUpdateRequest;
import com.mbc.puf.board.service.InquiryService;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 고객 마이페이지의 1:1 문의(CP-03). 전부 본인 문의만 다룬다 */
@Tag(name = "고객 1:1 문의", description = "마이페이지 1:1 문의 (본인 문의만)")
@RestController
@RequestMapping("/api/customers/me/inquiries")
@PreAuthorize("hasRole('CUSTOMER')")
public class InquiryController {

    private final InquiryService inquiryService;

    public InquiryController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @Operation(summary = "내 문의 목록 (페이징)")
    @GetMapping
    public ApiResponse<PageResponse<InquiryListItem>> list(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(inquiryService.getMyInquiries(loginUser.id(), page, size));
    }

    @Operation(summary = "내 문의 상세 (답변 포함)")
    @GetMapping("/{id}")
    public ApiResponse<InquiryDetailResponse> detail(@AuthenticationPrincipal LoginUser loginUser,
                                                      @PathVariable("id") Long id) {
        return ApiResponse.ok(inquiryService.getMyInquiry(loginUser.id(), id));
    }

    @Operation(summary = "문의 등록")
    @PostMapping
    public ResponseEntity<ApiResponse<InquiryDetailResponse>> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody InquiryCreateRequest request) {
        InquiryDetailResponse created = inquiryService.createInquiry(loginUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("문의가 등록되었습니다.", created));
    }

    // 수정: 아직 답변이 달리지 않은 내 문의만
    @Operation(summary = "문의 수정 (답변 전에만 가능)")
    @PutMapping("/{id}")
    public ApiResponse<InquiryDetailResponse> update(@AuthenticationPrincipal LoginUser loginUser,
                                                      @PathVariable("id") Long id,
                                                      @Valid @RequestBody InquiryUpdateRequest request) {
        return ApiResponse.ok("문의가 수정되었습니다.", inquiryService.updateMyInquiry(loginUser.id(), id, request));
    }

    // 삭제: 아직 답변이 달리지 않은 내 문의만 (소프트 삭제)
    @Operation(summary = "문의 삭제 (답변이 있어도 가능, 답변도 함께 삭제)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable("id") Long id) {
        inquiryService.deleteMyInquiry(loginUser.id(), id);
        return ApiResponse.ok("문의가 삭제되었습니다.", null);
    }
}