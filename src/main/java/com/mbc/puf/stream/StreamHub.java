package com.mbc.puf.stream;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mbc.puf.member.repository.ManagerRegionRepository;

import jakarta.annotation.PreDestroy;

/**
 * SSE 연결 목록을 들고 있다가 이벤트를 보내는 곳.
 * 서버가 1대라서 메모리 목록으로 충분하다 (서버를 여러 대로 늘리면 Redis pub/sub 등이 필요).
 *
 * - 연결 유형 두 가지: 고객 스트림(/api/stream, 비로그인 포함) / 관리자 스트림(/api/admin/stream)
 * - 전송은 전용 스레드 1개가 순서대로 처리한다 (느린 클라이언트가 DB 처리 스레드를 붙잡지 않게)
 * - 15초마다(설정 가능) heartbeat 이벤트를 보내 프록시가 연결을 끊지 않게 하고, 끊어진 연결을 정리한다
 * - 재연결 시 놓친 이벤트를 다시 보내지는 않는다. 프런트가 'connected' 이벤트를 받으면 REST 로 현재 상태를 다시 읽는다
 */
@Component
public class StreamHub {

    private static final Logger log = LoggerFactory.getLogger(StreamHub.class);

    /** 연결 1건의 정보 */
    private static class Connection {
        final SseEmitter emitter;
        final Long customerId;      // 로그인한 고객 id (비로그인/관리자 세션이면 null)
        final Long managerId;       // 관리자 스트림일 때만 값 있음
        final Set<Long> regionIds;  // 관리자 담당 지역 (연결 시점 기준)

        Connection(SseEmitter emitter, Long customerId, Long managerId, Set<Long> regionIds) {
            this.emitter = emitter;
            this.customerId = customerId;
            this.managerId = managerId;
            this.regionIds = regionIds;
        }

        boolean isAdmin() {
            return managerId != null;
        }
    }

    private final List<Connection> connections = new CopyOnWriteArrayList<>();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sse-sender");
        t.setDaemon(true);
        return t;
    });
    private final ManagerRegionRepository managerRegionRepository;

    public StreamHub(ManagerRegionRepository managerRegionRepository) {
        this.managerRegionRepository = managerRegionRepository;
    }

    /** 고객 스트림 연결. customerId 가 null 이면 비로그인(또는 관리자 세션)으로 공개 이벤트만 받는다 */
    public SseEmitter connectCustomer(Long customerId) {
        Connection c = register(new Connection(newEmitter(), customerId, null, Set.of()));
        sendConnected(c, customerId != null ? "CUSTOMER" : "ANONYMOUS");
        return c.emitter;
    }

    /** 관리자 스트림 연결. 담당 지역은 연결할 때 읽는다 (담당 지역이 바뀌면 재연결 시 반영) */
    public SseEmitter connectManager(Long managerId) {
        Set<Long> regionIds = new HashSet<>(managerRegionRepository.findRegionIds(managerId));
        Connection c = register(new Connection(newEmitter(), null, managerId, regionIds));
        sendConnected(c, "MANAGER");
        return c.emitter;
    }

    /** 이벤트를 보낼 대상을 골라 전송 (전용 스레드에서 처리) */
    public void dispatch(StreamEvent event) {
        sender.execute(() -> {
            for (Connection c : connections) {
                if (matches(c, event)) {
                    send(c, SseEmitter.event().name(event.name()).data(event.data(), MediaType.APPLICATION_JSON));
                }
            }
        });
    }

    /** 현재 연결 수 (점검/테스트용) */
    public Map<String, Object> stats() {
        long admin = connections.stream().filter(Connection::isAdmin).count();
        long customerLogin = connections.stream().filter(c -> c.customerId != null).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", connections.size());
        m.put("admin", admin);
        m.put("customerLoggedIn", customerLogin);
        m.put("anonymousOrOther", connections.size() - admin - customerLogin);
        return m;
    }

    private boolean matches(Connection c, StreamEvent e) {
        switch (e.scope()) {
        case PUBLIC:
            return c.isAdmin() ? inRegion(c, e.regionId()) : true;
        case CUSTOMER:
            return !c.isAdmin() && c.customerId != null && c.customerId.equals(e.targetId());
        case MANAGER:
            return c.isAdmin() && c.managerId.equals(e.targetId());
        case ADMIN:
            return c.isAdmin() && inRegion(c, e.regionId());
        default:
            return false;
        }
    }

    private boolean inRegion(Connection c, Long regionId) {
        return regionId == null || c.regionIds.contains(regionId);
    }

    // 주기적으로 heartbeat 이벤트(기본 15초, puf.stream.heartbeat-seconds).
    //  - 프록시(Render, Nginx 등)가 데이터 없는 유휴 연결을 끊지 않게 한다
    //  - 죽은 연결을 청소한다 (전송이 실패한 연결은 목록에서 빠진다)
    //  - 이름 있는 이벤트(heartbeat)로 보낸다: 주석 줄(": ping")은 브라우저 JS 에 안 보이지만, 이 이벤트는 보여서
    //    프런트가 "한동안 신호가 없으면 연결이 죽은 것" 으로 판단해 다시 연결할 수 있다 (앱이 백그라운드에서 돌아온 직후 등)
    // 느린 클라이언트가 공용 스케줄러 스레드를 붙잡지 않도록 전송 전용 스레드에서 보낸다
    @Scheduled(fixedRateString = "#{${puf.stream.heartbeat-seconds:15} * 1000}")
    public void heartbeat() {
        if (connections.isEmpty()) {
            return;
        }
        sender.execute(() -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("t", System.currentTimeMillis());
            for (Connection c : connections) {
                send(c, SseEmitter.event().comment(PAD).name("heartbeat").data(data, MediaType.APPLICATION_JSON));
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        sender.shutdownNow();
        for (Connection c : connections) {
            try {
                c.emitter.complete();
            } catch (Exception ignored) {
                // 종료 중이라 무시
            }
        }
        connections.clear();
    }

    // 프록시(Render/Cloudflare 등)가 작은 조각을 모아 두었다가 늦게 내보내는 것을 막기 위한 공백 주석(약 2KB). 브라우저 JS 에는 보이지 않는다
    private static final String PAD = " ".repeat(2048);

    private SseEmitter newEmitter() {
        return new SseEmitter(60L * 60 * 1000);   // 1시간 뒤 서버가 닫는다. 프런트(EventSource)가 자동 재연결
    }

    private Connection register(Connection c) {
        connections.add(c);
        Runnable remove = () -> connections.remove(c);
        c.emitter.onCompletion(remove);
        c.emitter.onTimeout(remove);
        c.emitter.onError(e -> remove.run());
        return c;
    }

    // 연결 직후 한 번 보내는 이벤트. 프런트는 이걸 받으면(재연결 포함) REST 로 현재 상태를 다시 읽는다
    private void sendConnected(Connection c, String userType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serverTime", LocalDateTime.now().withNano(0).toString());
        data.put("userType", userType);
        send(c, SseEmitter.event().comment(PAD).name("connected").reconnectTime(3000).data(data, MediaType.APPLICATION_JSON));
    }

    // 한 연결에 전송. 같은 emitter 에 동시에 쓰지 않도록 잠그고, 실패하면 연결을 정리한다!
    private void send(Connection c, SseEmitter.SseEventBuilder builder) {
        try {
            synchronized (c) {
                c.emitter.send(builder);
            }
        } catch (IOException | IllegalStateException e) {
            connections.remove(c);
            try {
                c.emitter.complete();   // completeWithError 는 에러 디스패치를 일으켜 GlobalExceptionHandler 가 SSE 응답에 JSON 을 쓰려다 또 실패한다
            } catch (Exception ignored) {
                // 이미 닫힌 연결
            }
            log.debug("SSE 연결 정리: {}", e.getMessage());
        }
    }
}