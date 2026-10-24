package com.mbc.puf.notification.client;

import com.mbc.puf.notification.dto.PushMessage;
import com.mbc.puf.notification.dto.PushResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Firebase Cloud Messaging(HTTP v1)으로 실제 푸시를 보낸다 (puf.fcm.enabled=true 일 때만).
 *
 * Firebase Admin SDK 대신 서비스 계정 JSON 으로 직접 인증한다 (의존성을 늘리지 않기 위해).
 *  1) 서비스 계정의 private key 로 JWT(RS256)를 만들어 OAuth 토큰 주소에 보내 액세스 토큰을 받는다 (1시간짜리, 캐시)
 *  2) POST {api-base}/v1/projects/{project_id}/messages:send 로 토큰 한 개씩 보낸다
 * 서비스 계정 JSON 파일은 Git 에 올리지 않는다 (puf.fcm.credentials-path = 파일 경로).
 */
@Component
@ConditionalOnProperty(name = "puf.fcm.enabled", havingValue = "true")
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RestClient restClient;
    private final String apiBase;
    private final String clickUrl;
    private final String androidChannelId;
    private final String projectId;
    private final String clientEmail;
    private final String tokenUri;
    private final PrivateKey privateKey;

    private String accessToken;
    private long accessTokenExpiresAtMillis;

    public FcmPushSender(@Value("${puf.fcm.credentials-path:}") String credentialsPath,
                         @Value("${puf.fcm.credentials-json:}") String credentialsJson,
                         @Value("${puf.fcm.api-base:https://fcm.googleapis.com}") String apiBase,
                         @Value("${puf.fcm.click-url:}") String clickUrl,
                         @Value("${puf.fcm.android-channel-id:}") String androidChannelId,
                         @Value("${puf.fcm.timeout-seconds:5}") int timeoutSeconds) {
        boolean hasJson = credentialsJson != null && !credentialsJson.isBlank();
        if (!hasJson && (credentialsPath == null || credentialsPath.isBlank())) {
            throw new IllegalStateException("puf.fcm.enabled=true 인데 puf.fcm.credentials-json(서비스 계정 JSON 전체, 환경변수 SECRET_FCM_CREDENTIALS_JSON) 또는 puf.fcm.credentials-path(파일 경로)가 비어 있습니다.");
        }
        try {
            JsonNode json = mapper.readTree(hasJson ? credentialsJson.trim()
                    : Files.readString(Path.of(credentialsPath.trim()), StandardCharsets.UTF_8));
            this.projectId = required(json, "project_id");
            this.clientEmail = required(json, "client_email");
            this.tokenUri = json.path("token_uri").asString("https://oauth2.googleapis.com/token");
            this.privateKey = parsePrivateKey(required(json, "private_key"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("FCM 서비스 계정 JSON 을 읽지 못했습니다: " + (hasJson ? "(환경변수)" : credentialsPath), e);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("FCM 서비스 계정의 private_key 형식이 올바르지 않습니다.", e);
        }
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.clickUrl = clickUrl == null ? "" : clickUrl.trim();
        this.androidChannelId = androidChannelId == null ? "" : androidChannelId.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        log.info("[push] FCM 사용: project={}", projectId);
    }

    private static String required(JsonNode json, String field) {
        String v = json.path(field).asString("");
        if (v.isBlank()) {
            throw new IllegalStateException("FCM 서비스 계정 JSON 에 " + field + " 가 없습니다.");
        }
        return v;
    }

    private static PrivateKey parsePrivateKey(String pem) throws java.security.GeneralSecurityException {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    @Override
    public PushResult send(String token, PushMessage message) {
        try {
            return post(token, message, false);
        } catch (RuntimeException e) {
            log.warn("[push] FCM 호출 실패: {}", e.toString());
            return PushResult.FAIL;
        }
    }

    private PushResult post(String token, PushMessage message, boolean retried) {
        try {
            restClient.post().uri(apiBase + "/v1/projects/" + projectId + "/messages:send")
                    .header("Authorization", "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildBody(token, message))
                    .retrieve().toBodilessEntity();
            return PushResult.SUCCESS;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String body = e.getResponseBodyAsString();
            if (status == 401 && !retried) {
                invalidateAccessToken();       // 액세스 토큰이 만료·취소됐으면 새로 받아 한 번만 다시 시도
                return post(token, message, true);
            }
            if (status == 404 || body.contains("UNREGISTERED") || body.contains("SENDER_ID_MISMATCH")
                    || (status == 400 && body.contains("INVALID_ARGUMENT") && body.toLowerCase().contains("token"))) {
                return PushResult.INVALID_TOKEN;
            }
            log.warn("[push] FCM 응답 {}: {}", status, body.length() > 300 ? body.substring(0, 300) : body);
            return PushResult.FAIL;
        } catch (RestClientException e) {
            log.warn("[push] FCM 연결 실패: {}", e.toString());
            return PushResult.FAIL;
        }
    }

    private String buildBody(String token, PushMessage message) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("token", token);
        msg.put("notification", Map.of("title", message.title(), "body", message.body() == null ? "" : message.body()));
        if (!message.data().isEmpty()) {
            msg.put("data", message.data());
        }
        // 앱(안드로이드): 앱이 꺼져 있어도 소리·진동이 나게 기본 알림음 + 진동 패턴을 같이 보낸다
        Map<String, Object> androidNotification = new LinkedHashMap<>();
        androidNotification.put("sound", "default");
        androidNotification.put("notification_priority", "PRIORITY_HIGH");
        androidNotification.put("vibrate_timings", List.of("0s", "0.3s", "0.12s", "0.3s", "0.12s", "0.6s"));
        if (!androidChannelId.isEmpty()) {
            androidNotification.put("channel_id", androidChannelId);   // 앱에 만들어 둔 알림 채널 (소리·진동 설정이 채널에 묶여 있음)
        }
        msg.put("android", Map.of("priority", "HIGH", "notification", androidNotification));
        Map<String, Object> webpush = new LinkedHashMap<>();
        webpush.put("headers", Map.of("Urgency", "high"));
        if (clickUrl.startsWith("https://")) {
            webpush.put("fcm_options", Map.of("link", clickUrl));   // 웹 푸시를 눌렀을 때 열 주소 (https 만 허용됨)
        }
        // PWA(웹 푸시): 진동 패턴 + 누를 때까지 알림 유지 (웹 알림은 소리를 따로 정할 수 없어 기기 알림음을 따른다)
        webpush.put("notification", Map.of("vibrate", List.of(300, 120, 300, 120, 600), "requireInteraction", true));
        msg.put("webpush", webpush);
        return mapper.writeValueAsString(Map.of("message", msg));
    }

    // ---------------- OAuth 액세스 토큰 ----------------

    private synchronized String accessToken() {
        long now = System.currentTimeMillis();
        if (accessToken != null && now < accessTokenExpiresAtMillis - 60_000) {
            return accessToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.add("assertion", signedJwt(now / 1000));
        JsonNode res = restClient.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(JsonNode.class);
        String token = res == null ? "" : res.path("access_token").asString("");
        if (token.isBlank()) {
            throw new IllegalStateException("FCM 액세스 토큰을 받지 못했습니다.");
        }
        accessToken = token;
        accessTokenExpiresAtMillis = now + res.path("expires_in").asLong(3600) * 1000;
        return accessToken;
    }

    private synchronized void invalidateAccessToken() {
        accessToken = null;
    }

    private String signedJwt(long nowSec) {
        try {
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", clientEmail);
            claims.put("scope", SCOPE);
            claims.put("aud", tokenUri);
            claims.put("iat", nowSec);
            claims.put("exp", nowSec + 3600);
            String signingInput = b64(mapper.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT"))) + "."
                    + b64(mapper.writeValueAsBytes(claims));
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + b64(sig.sign());
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("FCM JWT 서명에 실패했습니다.", e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
