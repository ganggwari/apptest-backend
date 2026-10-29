package com.mbc.puf.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 로그인 토큰 발급·검증. iOS Safari 는 다른 사이트(프런트와 백엔드가 서로 다른 도메인)가 준 쿠키를 막거나 금방 지워서
 * 세션 쿠키(JSESSIONID)만으로는 로그인이 풀린다. 그래서 로그인할 때 서명된 토큰도 함께 내려주고,
 * 프런트가 그 토큰을 Authorization 헤더(SSE 는 access_token 쿼리)로 보내면 쿠키 없이도 로그인된 것으로 본다.
 *
 * 토큰 형식: base64url(id.type.role.만료epochSec) + "." + base64url(HMAC-SHA256). 서버가 상태를 저장하지 않으므로
 * 서버가 재시작돼도 만료 전까지 유효하다. 비밀값(puf.auth.token-secret)을 바꾸면 기존 토큰이 모두 무효가 된다.
 */
@Component
public class AuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenService.class);
    private static final String DEFAULT_SECRET = "puf-demo-token-secret-please-change";

    private final byte[] secret;
    private final long ttlSeconds;

    public AuthTokenService(@Value("${puf.auth.token-secret:}") String secret,
                            @Value("${puf.auth.token-ttl-hours:12}") long ttlHours) {
        if (secret == null || secret.isBlank()) {
            log.warn("puf.auth.token-secret 이 비어 있어 기본값을 쓴다. 운영에서는 PUF_AUTH_TOKEN_SECRET 을 설정할 것");
            secret = DEFAULT_SECRET;
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = Math.max(1, ttlHours) * 3600;
    }

    public String issue(LoginUser user) {
        long exp = System.currentTimeMillis() / 1000 + ttlSeconds;
        String payload = user.id() + "." + user.type() + "." + user.role() + "." + exp;
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + sign(body);
    }

    public Optional<LoginUser> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        String body = token.substring(0, dot);
        String signature = token.substring(dot + 1);
        // 서명 비교는 시간이 일정하게 걸리는 방식으로 한다
        if (!MessageDigest.isEqual(sign(body).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8);
            String[] parts = payload.split("\\.");
            if (parts.length != 4) {
                return Optional.empty();
            }
            if (Long.parseLong(parts[3]) < System.currentTimeMillis() / 1000) {
                return Optional.empty();
            }
            return Optional.of(new LoginUser(Long.valueOf(parts[0]), parts[1], parts[2]));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("토큰 서명 실패", e);
        }
    }
}
