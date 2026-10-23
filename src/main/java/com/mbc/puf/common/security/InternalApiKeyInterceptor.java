package com.mbc.puf.common.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * FastAPI 내부 API(/api/internal/**)용 인증. 세션이 아니라 X-Internal-Api-Key 헤더 값을
 * 고정값과 대조한다. SecurityConfig에서 이 경로를 permitAll로 열어두고, 대신 이 인터셉터가
 * (WebConfigurer에 등록됨) 컨트롤러 진입 전에 헤더를 검사해서 막는다
 */
@Component
public class InternalApiKeyInterceptor implements HandlerInterceptor {

    private static final String HEADER_NAME = "X-Internal-Api-Key";

    @Value("${puf.internal.api-key}")
    private String expectedApiKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String providedKey = request.getHeader(HEADER_NAME);
        if (providedKey == null || !providedKey.equals(expectedApiKey)) {
            write(response, HttpStatus.UNAUTHORIZED, "유효하지 않은 내부 API 키입니다.");
            return false;
        }
        return true;
    }

    // 고정 문구만 쓰므로 JSON을 직접 조립한다 (JsonAuthErrorHandler와 같은 방식)
    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\",\"data\":null}");
    }
}
