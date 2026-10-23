package com.mbc.puf.notification.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 프런트(PWA)가 FCM 웹 푸시 토큰을 받는 데 필요한 공개 설정. 로그인 없이 조회한다. */
@Tag(name = "푸시", description = "웹 푸시 설정")
@RestController
@RequestMapping("/api/push")
public class PushConfigController {

    public record WebPushConfig(boolean enabled, String apiKey, String projectId, String senderId, String appId,
                                String vapidKey) {
    }

    private final String apiKey;
    private final String projectId;
    private final String senderId;
    private final String appId;
    private final String vapidKey;

    public PushConfigController(@Value("${puf.fcm.web.api-key:}") String apiKey,
                                @Value("${puf.fcm.web.project-id:}") String projectId,
                                @Value("${puf.fcm.web.sender-id:}") String senderId,
                                @Value("${puf.fcm.web.app-id:}") String appId,
                                @Value("${puf.fcm.web.vapid-key:}") String vapidKey) {
        this.apiKey = apiKey;
        this.projectId = projectId;
        this.senderId = senderId;
        this.appId = appId;
        this.vapidKey = vapidKey;
    }

    @Operation(summary = "웹 푸시 설정", description = "enabled=false 면 서버에 웹 푸시 설정이 없는 것(앱 화면은 SSE 로만 알림을 받는다).")
    @GetMapping("/config")
    public ApiResponse<WebPushConfig> config() {
        boolean enabled = !apiKey.isBlank() && !projectId.isBlank() && !senderId.isBlank() && !appId.isBlank() && !vapidKey.isBlank();
        return ApiResponse.ok(enabled ? new WebPushConfig(true, apiKey, projectId, senderId, appId, vapidKey)
                : new WebPushConfig(false, "", "", "", "", ""));
    }
}
