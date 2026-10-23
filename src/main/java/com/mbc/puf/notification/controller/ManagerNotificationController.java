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

import com.mbc.puf.common.enums.ManagerNotificationCategory;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.notification.dto.ManagerNotificationItem;
import com.mbc.puf.notification.dto.NotificationUnreadResponse;
import com.mbc.puf.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 관리자 알림 센터 (헤더 벨, 알림 센터 화면). 내 담당 지역 알림 + 전체 대상 알림, 읽음은 관리자별 */
@Tag(name = "관리자 알림 센터", description = "전체·위험·설비·업무·시스템 알림, 안 읽은 수, 읽음 처리")
@RestController
@RequestMapping("/api/managers/me/notifications")
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class ManagerNotificationController {

    private final NotificationService notificationService;

    public ManagerNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "내 알림 목록 (category 없으면 '전체' 탭, 안 읽은 것만 필터 가능)")
    @GetMapping
    public ApiResponse<PageResponse<ManagerNotificationItem>> list(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(name = "category", required = false) ManagerNotificationCategory category,
            @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly,
            @ParameterObject @ModelAttribute PageQuery page) {
        return ApiResponse.ok(notificationService.getManagerNotifications(loginUser, category, unreadOnly, page));
    }

    @Operation(summary = "안 읽은 알림 수 (헤더 벨 숫자 + 탭별 숫자)")
    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadResponse> unreadCount(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(notificationService.getManagerUnread(loginUser));
    }

    @Operation(summary = "알림 1건 읽음 처리 (나에게만 적용)")
    @PutMapping("/{id}/read")
    public ApiResponse<Void> read(@AuthenticationPrincipal LoginUser loginUser, @PathVariable("id") Long id) {
        notificationService.markManagerRead(loginUser, id);
        return ApiResponse.ok("읽음 처리되었습니다.", null);
    }

    @Operation(summary = "모두 읽음 (category 를 주면 그 분류만)")
    @PutMapping("/read-all")
    public ApiResponse<Void> readAll(@AuthenticationPrincipal LoginUser loginUser,
                                     @RequestParam(name = "category", required = false)
                                     ManagerNotificationCategory category) {
        notificationService.markManagerAllRead(loginUser, category);
        return ApiResponse.ok("모두 읽음 처리되었습니다.", null);
    }
}
