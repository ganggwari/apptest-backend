package com.mbc.puf.common.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 보안 필터 단계에서 발생한 401/403 을 ApiResponse 형태의 JSON 으로 내려준다.
 * (필터 단계 예외는 컨트롤러 이전에 발생해서 GlobalExceptionHandler 가 받을 수 없다)
 */
@Component
public class JsonAuthErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    // 401: 토큰이 없거나, 위조/만료된 토큰
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "인증이 필요합니다. 다시 로그인해주세요.");
    }

    // 403: 로그인은 했지만 이 API를 쓸 권한이 없음
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    // 고정 문구만 쓰므로 JSON 을 직접 조립한다 (Jackson 버전에 의존하지 않기 위함)
    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\",\"data\":null}");
    }
}
