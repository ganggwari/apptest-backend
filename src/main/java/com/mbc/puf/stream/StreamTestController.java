package com.mbc.puf.stream;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * [개발 전용] SSE 가 잘 가는지 손으로 확인하는 API. X-Internal-Api-Key 헤더가 필요하다.
 * 운영에서는 application.properties 의 puf.stream.test-endpoint 를 false 로 두면 이 컨트롤러가 아예 생기지 않는다.
 */
@Tag(name = "실시간 스트림(SSE)")
@RestController
@RequestMapping("/api/internal/stream")
@ConditionalOnProperty(name = "puf.stream.test-endpoint", havingValue = "true")
public class StreamTestController {

    /** 테스트 이벤트 요청. scope 는 PUBLIC/CUSTOMER/MANAGER/ADMIN */
    public record TestEventRequest(StreamScope scope, String name, Long regionId, Long targetId,
            Map<String, Object> data) {
    }

    private final StreamPublisher publisher;
    private final StreamHub hub;

    public StreamTestController(StreamPublisher publisher, StreamHub hub) {
        this.publisher = publisher;
        this.hub = hub;
    }

    @Operation(summary = "[개발용] 테스트 이벤트 발행", description = "원하는 범위로 임의 이벤트를 보내 SSE 연결을 확인한다")
    @PostMapping("/test")
    public ApiResponse<Void> publish(@RequestBody TestEventRequest r) {
        publisher.publish(new StreamEvent(r.name() != null ? r.name() : "test", r.data(),
                r.scope() != null ? r.scope() : StreamScope.PUBLIC, r.regionId(), r.targetId()));
        return ApiResponse.ok(null);
    }

    @Operation(summary = "[개발용] 현재 SSE 연결 수")
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(hub.stats());
    }
}
