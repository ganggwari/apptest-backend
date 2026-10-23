package com.mbc.puf.notification.service;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.google.auth.oauth2.GoogleCredentials;
import com.mbc.puf.common.enums.DevicePlatform;
import com.mbc.puf.member.entity.DeviceToken;
import com.mbc.puf.member.repository.DeviceTokenRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * FCM HTTP v1 로 푸시를 보낸다 (https://fcm.googleapis.com/v1/projects/{id}/messages:send).
 *
 * - 메시지는 "data 전용"으로 보낸다. 앱(안드로이드)은 FirebaseMessagingService 가, 웹은 서비스 워커의 push 이벤트가
 *   data 를 받아 직접 알림을 띄운다 → 앱이 꺼져 있어도, 화면 구성을 앱이 정할 수 있다.
 * - 인증: 서비스 계정 JSON(puf.fcm.credentials-json 또는 credentials-file). 없으면 비활성.
 * - 발송은 별도 스레드에서, 알림을 저장한 트랜잭션이 커밋된 뒤에 한다. 푸시 실패가 알림 저장을 막지 않는다.
 * - FCM 이 "이 토큰은 더 이상 없다"(UNREGISTERED/404)고 알려 주면 그 토큰은 사용 중지한다.
 */
@Service
public class PushServiceImpl implements PushService {

    private static final Logger log = LoggerFactory.getLogger(PushServiceImpl.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final DeviceTokenRepository deviceTokenRepository;
    private final ObjectMapper objectMapper;
    private final String credentialsJson;
    private final String credentialsFile;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "fcm-sender");
        t.setDaemon(true);
        return t;
    });

    private GoogleCredentials credentials;
    private String projectId;

    public PushServiceImpl(DeviceTokenRepository deviceTokenRepository, ObjectMapper objectMapper,
                           @Value("${puf.fcm.credentials-json:}") String credentialsJson,
                           @Value("${puf.fcm.credentials-file:}") String credentialsFile) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.objectMapper = objectMapper;
        this.credentialsJson = credentialsJson == null ? "" : credentialsJson.trim();
        this.credentialsFile = credentialsFile == null ? "" : credentialsFile.trim();
    }

    @PostConstruct
    void init() {
        try {
            byte[] json = null;
            if (!credentialsJson.isEmpty()) {
                json = credentialsJson.getBytes(StandardCharsets.UTF_8);
            } else if (!credentialsFile.isEmpty()) {
                try (InputStream in = new FileInputStream(credentialsFile)) {
                    json = in.readAllBytes();
                }
            }
            if (json == null) {
                log.info("[FCM] 설정이 없어 푸시를 보내지 않습니다 (SSE 알림은 그대로 동작). "
                        + "켜려면 SECRET_FCM_CREDENTIALS_JSON 에 서비스 계정 JSON 을 넣으세요.");
                return;
            }
            projectId = objectMapper.readTree(json).get("project_id").asText();
            credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(json)).createScoped(List.of(SCOPE));
            log.info("[FCM] 푸시 발송을 켭니다 (project={})", projectId);
        } catch (Exception e) {
            credentials = null;
            log.warn("[FCM] 서비스 계정을 읽지 못해 푸시를 끕니다: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public boolean isEnabled() {
        return credentials != null;
    }

    @Override
    public void sendToCustomer(Long customerId, String title, String body, String refType, Long refId) {
        if (!isEnabled() || customerId == null) return;
        afterCommit(() -> executor.execute(() ->
                deliver(deviceTokenRepository.findByCustomerIdAndActiveTrueOrderByIdAsc(customerId), title, body, refType, refId)));
    }

    @Override
    public void sendToAllCustomers(String title, String body, String refType, Long refId) {
        if (!isEnabled()) return;
        afterCommit(() -> executor.execute(() ->
                deliver(deviceTokenRepository.findAllByActiveTrue(), title, body, refType, refId)));
    }

    // 알림을 저장한 트랜잭션이 커밋된 뒤에 보낸다 (롤백되면 보내지 않는다). 트랜잭션 밖이면 바로 실행
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void deliver(List<DeviceToken> tokens, String title, String body, String refType, Long refId) {
        if (tokens.isEmpty()) return;
        String accessToken;
        try {
            credentials.refreshIfExpired();
            accessToken = credentials.getAccessToken().getTokenValue();
        } catch (IOException | RuntimeException e) {
            log.warn("[FCM] 인증 토큰을 받지 못했습니다: {}", e.getMessage());
            return;
        }
        for (DeviceToken token : tokens) {
            try {
                send(accessToken, token, title, body, refType, refId);
            } catch (Exception e) {
                log.warn("[FCM] 발송 실패 (token id={}): {}", token.getId(), e.getMessage());
            }
        }
    }

    private void send(String accessToken, DeviceToken token, String title, String body, String refType, Long refId)
            throws IOException, InterruptedException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode message = root.putObject("message");
        message.put("token", token.getToken());
        ObjectNode data = message.putObject("data");   // data 값은 모두 문자열이어야 한다
        data.put("title", title == null ? "" : title);
        data.put("body", body == null ? "" : body);
        data.put("refType", refType == null ? "" : refType);
        data.put("refId", refId == null ? "" : String.valueOf(refId));
        data.put("url", clickUrl(refType));
        data.put("tag", refType == null ? "puf" : refType + (refId == null ? "" : "-" + refId));
        message.putObject("android").put("priority", "HIGH");   // 앱이 대기 상태여도 바로 깨운다
        ObjectNode webpush = message.putObject("webpush");
        ObjectNode headers = webpush.putObject("headers");
        headers.put("Urgency", "high");
        headers.put("TTL", "3600");

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        if (status / 100 == 2) {
            log.debug("[FCM] 발송 완료 (token id={}, {})", token.getId(), token.getPlatform());
            return;
        }
        String resBody = response.body() == null ? "" : response.body();
        // 앱 삭제·토큰 만료: 더는 보낼 수 없는 토큰이라 사용 중지한다
        if (status == 404 || resBody.contains("UNREGISTERED") || resBody.contains("INVALID_ARGUMENT")) {
            deviceTokenRepository.deactivateByToken(token.getToken());
            log.info("[FCM] 사용할 수 없는 토큰이라 중지했습니다 (id={}, {}, status={})", token.getId(),
                    token.getPlatform() == DevicePlatform.APP ? "APP" : "WEB", status);
        } else {
            log.warn("[FCM] 발송 거절 status={} body={}", status, resBody.length() > 300 ? resBody.substring(0, 300) : resBody);
        }
    }

    // 알림을 눌렀을 때 열 화면. 프런트 라우트와 맞춘다
    private static String clickUrl(String refType) {
        if (refType == null) return "/notifications";
        return switch (refType) {
            case "ALERT" -> "/my-alerts";
            case "NOTICE" -> "/c-notice";
            case "INQUIRY" -> "/my-inquiries";
            default -> "/notifications";
        };
    }
}
