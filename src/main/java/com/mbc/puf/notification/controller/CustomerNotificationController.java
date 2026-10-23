package com.mbc.puf.notification.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.enums.CustomerNotificationCategory;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.notification.dto.CustomerNotificationItem;
import com.mbc.puf.notification.dto.NotificationUnreadResponse;
import com.mbc.puf.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 고객 알림 센터 (마이페이지 알림 내역, 헤더 종 아이콘). 위험/공지/문의 3분류, 본인 알림만 */
@Tag(name = "고객 알림 센터", description = "위험·공지·문의 알림 내역, 안 읽은 수, 읽음 처리")
@RestController
@RequestMapping("/api/customers/me/notifications")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerNotificationController {

    private final NotificationService notificationService;

    public CustomerNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "내 알림 목록 (분류 칩·안 읽은 것만 필터, 최신순)")
    @GetMapping
    public ApiResponse<PageResponse<CustomerNotificationItem>> list(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(name = "category", required = false) CustomerNotificationCategory category,
            @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly,
            @ParameterObject @ModelAttribute PageQuery page) {
        return ApiResponse.ok(notificationService.getMyNotifications(loginUser.id(), category, unreadOnly, page));
    }

    @Operation(summary = "안 읽은 알림 수 (종 아이콘 숫자 + 분류별)")
    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadResponse> unreadCount(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(notificationService.getMyUnread(loginUser.id()));
    }

    @Operation(summary = "알림 1건 읽음 처리")
    @PutMapping("/{id}/read")
    public ApiResponse<Void> read(@AuthenticationPrincipal LoginUser loginUser, @PathVariable("id") Long id) {
        notificationService.markMyRead(loginUser.id(), id);
        return ApiResponse.ok("읽음 처리되었습니다.", null);
    }

    @Operation(summary = "모두 읽음 (category 를 주면 그 분류만)")
    @PutMapping("/read-all")
    public ApiResponse<Void> readAll(@AuthenticationPrincipal LoginUser loginUser,
                                     @RequestParam(name = "category", required = false)
                                     CustomerNotificationCategory category) {
        notificationService.markMyAllRead(loginUser.id(), category);
        return ApiResponse.ok("모두 읽음 처리되었습니다.", null);
    }
}
