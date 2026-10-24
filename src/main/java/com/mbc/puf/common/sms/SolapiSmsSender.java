package com.mbc.puf.common.sms;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

import com.mbc.puf.common.exception.BusinessException;

import tools.jackson.databind.json.JsonMapper;

/**
 * Solapi(솔라피)로 실제 문자를 보낸다 (puf.sms.enabled=true 일 때만 만들어진다).
 * 켜지 않으면 StubSmsSender(콘솔 출력)가 쓰인다. 둘은 같은 스위치로 서로 배타적이라 동시에 만들어지지 않는다.
 * 인증: Authorization: HMAC-SHA256 apiKey=..., date=..., salt=..., signature=HMAC-SHA256(secret, date+salt)
 * 보내는 번호(from)는 Solapi 콘솔에 등록·인증된 발신번호여야 한다.
 */
@Component
@ConditionalOnProperty(name = "puf.sms.enabled", havingValue = "true")
public class SolapiSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(SolapiSmsSender.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SecureRandom random = new SecureRandom();
    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;
    private final String apiSecret;
    private final String from;

    public SolapiSmsSender(@Value("${puf.sms.api-key:}") String apiKey,
                           @Value("${puf.sms.api-secret:}") String apiSecret,
                           @Value("${puf.sms.from:}") String from,
                           @Value("${puf.sms.base-url:https://api.solapi.com}") String baseUrl,
                           @Value("${puf.sms.timeout-seconds:10}") int timeoutSeconds) {
        if (apiKey.isBlank() || apiSecret.isBlank() || from.isBlank()) {
            throw new IllegalStateException(
                    "puf.sms.enabled=true 인데 puf.sms.api-key / api-secret / from (secret.sms.*) 가 비어 있습니다.");
        }
        this.apiKey = apiKey.trim();
        this.apiSecret = apiSecret.trim();
        this.from = from.replaceAll("\\D", "");
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void send(String phone, String message) {
        String body = mapper.writeValueAsString(Map.of("message", Map.of("to", phone, "from", from, "text", message)));
        try {
            restClient.post().uri(baseUrl + "/messages/v4/send")
                    .header("Authorization", authorization(Instant.now(), newSalt()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[문자 발송] 받는 번호 끝 4자리 {}", phone.substring(Math.max(0, phone.length() - 4)));
        } catch (RestClientResponseException e) {
            // 키·발신번호 오류 등은 Solapi 가 오류 코드와 메시지를 본문으로 알려 준다 (로그에만 남기고 사용자에게는 일반 문구)
            log.error("[문자 발송 실패] HTTP {} {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "문자를 보내지 못했습니다. 잠시 후 다시 시도해주세요.");
        } catch (RestClientException e) {
            log.error("[문자 발송 실패] 연결 오류: {}", e.getMessage());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "문자를 보내지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    /** 테스트에서 서명을 다시 계산해 볼 수 있게 package-private */
    String authorization(Instant now, String salt) {
        String date = DATE.format(now);
        return "HMAC-SHA256 apiKey=" + apiKey + ", date=" + date + ", salt=" + salt
                + ", signature=" + sign(date + salt);
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("문자 서명을 만들지 못했습니다.", e);
        }
    }

    private String newSalt() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
