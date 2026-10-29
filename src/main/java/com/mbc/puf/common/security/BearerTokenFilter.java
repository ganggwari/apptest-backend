package com.mbc.puf.common.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 세션 쿠키가 없는 요청(iOS Safari 의 크로스사이트 쿠키 차단 등)을 토큰으로 로그인 처리한다.
 * 이미 세션으로 로그인된 요청은 건드리지 않는다. 토큰은 Authorization: Bearer 헤더로 받고,
 * EventSource(SSE)는 헤더를 못 붙이므로 스트림 경로에 한해 access_token 쿼리도 받는다.
 * 서블릿 필터로 자동 등록되지 않게 @Component 를 붙이지 않고 SecurityConfig 에서만 만든다.
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    private final AuthTokenService tokens;

    public BearerTokenFilter(AuthTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null || current instanceof AnonymousAuthenticationToken || !current.isAuthenticated()) {
            String token = extract(request);
            if (token != null) {
                tokens.verify(token).ifPresent(user -> {
                    var authentication = UsernamePasswordAuthenticationToken.authenticated(
                            user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        }
        chain.doFilter(request, response);
    }

    private static String extract(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        String path = request.getRequestURI();
        if ("/api/stream".equals(path) || "/api/admin/stream".equals(path)) {
            return request.getParameter("access_token");
        }
        return null;
    }
}
