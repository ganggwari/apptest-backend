package com.mbc.puf.navigation.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.mbc.puf.navigation.service.HazardService;

/** 폰이 /ws/navigation 에 접속하면 현재 위험 상태를 바로 알려주고, 이후 변경은 NavigationWsHub 가 뿌려준다 */
@Component
public class NavigationWsHandler extends TextWebSocketHandler {

    private final NavigationWsHub hub;
    private final HazardService hazardService;

    public NavigationWsHandler(NavigationWsHub hub, HazardService hazardService) {
        this.hub = hub;
        this.hazardService = hazardService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        hub.register(session);
        hub.sendTo(session, "state", hazardService.state());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 폰 -> 서버 메시지는 아직 쓰지 않는다 (서버가 일방적으로 알려주는 용도)
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        hub.unregister(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.unregister(session);
    }
}
