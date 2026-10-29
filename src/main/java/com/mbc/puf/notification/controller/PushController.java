package com.mbc.puf.notification.controller;

import com.mbc.puf.notification.dto.PushMessage;
import com.mbc.puf.notification.service.PushService;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.security.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "푸시", description = "푸시 알림 시험 발송")
@RestController
@RequestMapping("/api/customers/me")
public class PushController {

    private final PushService pushService;

    public PushController(PushService pushService) {
        this.pushService = pushService;
    }

    @Operation(summary = "내 기기로 시험 푸시 보내기",
            description = "내 사용 중인 푸시 토큰 전부에 시험 알림을 바로 보내고 결과 개수를 돌려준다. "
                    + "토큰을 등록(POST /api/customers/me/device-tokens)한 뒤 푸시가 오는지 확인할 때 쓴다. puf.fcm.enabled=false 면 실제로는 나가지 않고 서버 로그만 남는다. 고객 로그인 필요.")
    @PostMapping("/push-test")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<PushService.TestResult> pushTest(@AuthenticationPrincipal LoginUser loginUser) {
        PushMessage message = new PushMessage("시험 알림", "푸시 알림이 정상적으로 도착했습니다.",
                Map.of("refType", "TEST", "url", "/customer-app/screen-12"));
        return ApiResponse.ok(pushService.sendTest(loginUser.id(), message));
    }
}
