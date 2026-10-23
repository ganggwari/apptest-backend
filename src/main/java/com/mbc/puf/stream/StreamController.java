package com.mbc.puf.stream;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mbc.puf.common.security.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "실시간 스트림(SSE)", description = "프런트 폴링을 대체하는 서버 푸시. EventSource 로 연결한다")
@RestController
@RequestMapping("/api")
public class StreamController {

    private final StreamHub hub;

    public StreamController(StreamHub hub) {
        this.hub = hub;
    }

    @Operation(summary = "고객용 실시간 스트림 연결",
            description = "비로그인도 연결 가능(공개 이벤트만 수신). 고객이 로그인한 상태면 내 알림 이벤트도 함께 받는다. "
                    + "브라우저: new EventSource('/api/stream', { withCredentials: true }). "
                    + "연결 직후 'connected' 이벤트가 오며, 재연결 시에도 오므로 그때 REST 로 현재 상태를 다시 조회한다. "
                    + "Swagger UI 에서는 스트림이 끝나지 않으니 Postman/브라우저로 확인한다.")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@AuthenticationPrincipal LoginUser loginUser) {
        Long customerId = loginUser != null && !loginUser.isManager() ? loginUser.id() : null;
        return sse(hub.connectCustomer(customerId));
    }

    @Operation(summary = "관리자용 실시간 스트림 연결",
            description = "관리자 로그인 필요. 내 담당 지역의 이벤트와 관리자 전용 이벤트(알림 등)를 받는다.")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    @GetMapping(value = "/admin/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> adminStream(@AuthenticationPrincipal LoginUser loginUser) {
        return sse(hub.connectManager(loginUser.id()));
    }

    // 캐시 금지 + Nginx 가 응답을 모아서 보내지 않도록(buffering off) 헤더 지정
    private ResponseEntity<SseEmitter> sse(SseEmitter emitter) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }
}
