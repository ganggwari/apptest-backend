package com.mbc.puf.common.security;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** 비밀번호 확인이 끝난 사용자를 세션에 로그인 상태로 기록하고, 로그아웃 시 세션을 없앤다. 고객/관리자가 함께 사용한다. */
@Component
public class SessionLoginManager {

    public static final String TOKEN_HEADER = "X-Auth-Token";

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final AuthTokenService tokens;

    public SessionLoginManager(AuthTokenService tokens) {
        this.tokens = tokens;
    }

    // 고객/관리자 공통. 관리자의 담당 지역은 세션에 넣지 않고 매 요청 DB에서 읽는다(ScopeResolver)
    public void login(HttpServletRequest request, HttpServletResponse response,
                      Long userId, String type, String role) {
        // 세션 고정 공격 방지: 로그인 전에 이미 세션이 있었다면 세션 ID를 새로 바꾼다
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        LoginUser principal = new LoginUser(userId, type, role);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // Spring Security 6 부터는 직접 인증한 결과를 세션에 명시적으로 저장해야 다음 요청에서도 유지된다
        securityContextRepository.saveContext(context, request, response);

        // iOS Safari 처럼 크로스사이트 쿠키가 막히는 환경을 위해 같은 로그인 상태를 토큰으로도 내려준다 (프런트가 이 헤더를 저장한다)
        response.setHeader(TOKEN_HEADER, tokens.issue(principal));
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();          // 서버에서 세션 삭제 → 그 세션 ID는 즉시 무효
        }
        SecurityContextHolder.clearContext();
    }
}