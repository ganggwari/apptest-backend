package com.mbc.puf.member.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.security.SessionLoginManager;

import jakarta.servlet.http.HttpServletRequest;

/** 고객/관리자 공통 인증 API */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SessionLoginManager sessionLoginManager;

    public AuthController(SessionLoginManager sessionLoginManager) {
        this.sessionLoginManager = sessionLoginManager;
    }

    // 로그아웃: 로그인 상태가 아니어도 오류 없이 성공으로 응답한다
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        sessionLoginManager.logout(request);
        return ApiResponse.ok("로그아웃되었습니다.", null);
    }
}
