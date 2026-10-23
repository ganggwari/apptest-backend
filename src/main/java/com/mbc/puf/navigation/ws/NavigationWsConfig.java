package com.mbc.puf.navigation.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 내비 WebSocket 주소 등록. 로그인한 사용자(세션 쿠키)만 접속할 수 있다 (SecurityConfig 의 anyRequest().authenticated()) */
@Configuration
@EnableWebSocket
public class NavigationWsConfig implements WebSocketConfigurer {

    private final NavigationWsHandler handler;
    private final String[] allowedOrigins;

    public NavigationWsConfig(NavigationWsHandler handler,
                              @Value("${puf.navigation.ws-allowed-origins:http://localhost:*}") String[] allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/navigation").setAllowedOriginPatterns(allowedOrigins);
    }
}
