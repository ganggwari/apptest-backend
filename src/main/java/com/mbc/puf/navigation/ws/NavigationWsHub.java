package com.mbc.puf.navigation.ws;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import tools.jackson.databind.json.JsonMapper;

/**
 * 내비에 접속 중인 모든 폰(WebSocket 세션)을 들고 있다가 한꺼번에 메시지를 쏴주는 곳.
 * 메시지 형식은 항상 {"type": "state" | "ping", "data": {...}} 한 덩어리 JSON.
 */
@Component
public class NavigationWsHub {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        // 하트비트 스레드와 요청 스레드가 동시에 같은 세션에 쓰지 않도록 보호막을 씌운다.
        // (5초 안에 못 보내거나 대기 데이터가 64KB 를 넘으면 그 세션은 끊는다)
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 5000, 64 * 1024));
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session.getId());
    }

    /** 특정 접속자 한 명에게만 전송 (접속 직후 현재 상태 알려줄 때 사용) */
    public void sendTo(WebSocketSession session, String type, Object data) {
        WebSocketSession target = sessions.get(session.getId());
        if (target != null) {
            send(target, toJson(type, data));
        }
    }

    /** 접속 중인 모두에게 전송 */
    public void broadcast(String type, Object data) {
        String json = toJson(type, data);
        for (WebSocketSession s : sessions.values()) {
            send(s, json);
        }
    }

    public int connectionCount() {
        return sessions.size();
    }

    /** 5초마다 "나 살아있어" 신호. 프런트가 이게 끊기면 연결이 죽었다고 판단한다 */
    @Scheduled(fixedRate = 5000)
    public void heartbeat() {
        if (!sessions.isEmpty()) {
            broadcast("ping", Map.of("t", System.currentTimeMillis()));
        }
    }

    private String toJson(String type, Object data) {
        return JSON.writeValueAsString(Map.of("type", type, "data", data));
    }

    private void send(WebSocketSession s, String json) {
        try {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(json));
            } else {
                sessions.remove(s.getId());
            }
        } catch (Exception e) {
            sessions.remove(s.getId());
        }
    }
}
