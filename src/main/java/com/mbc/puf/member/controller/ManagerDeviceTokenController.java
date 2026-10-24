package com.mbc.puf.member.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.member.dto.DeviceTokenRequest;
import com.mbc.puf.member.dto.DeviceTokenResponse;
import com.mbc.puf.member.entity.ManagerDeviceToken;
import com.mbc.puf.member.repository.ManagerDeviceTokenRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 관리자 푸시 토큰 등록·해제 (고객의 /api/customers/me/device-tokens 와 같은 방식) */
@Tag(name = "관리자 푸시 토큰", description = "관리자 알림(위험·설비 점검·데일리 리포트 등)을 기기로 받기 위한 토큰")
@RestController
@RequestMapping("/api/managers/me/device-tokens")
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class ManagerDeviceTokenController {

    private final ManagerDeviceTokenRepository repository;

    public ManagerDeviceTokenController(ManagerDeviceTokenRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "내 푸시 토큰 목록", description = "사용 중인 토큰의 id·platform·active 만 준다 (토큰 값은 돌려주지 않는다)")
    @GetMapping
    public ApiResponse<List<DeviceTokenResponse>> list(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(repository.findByManagerIdAndActiveTrueOrderByIdAsc(loginUser.id()).stream()
                .map(t -> new DeviceTokenResponse(t.getId(), t.getPlatform(), t.isActive())).toList());
    }

    @Operation(summary = "푸시 토큰 등록", description = "로그인 후 웹/앱이 발급받은 토큰을 보낸다. 같은 토큰이 이미 있으면 주인을 나로 바꾸고 다시 켠다 "
            + "(같은 기기에서 다른 계정으로 로그인한 경우도 이전 계정 알림이 이 기기로 오지 않게 된다)")
    @PostMapping
    @Transactional
    public ApiResponse<DeviceTokenResponse> register(@AuthenticationPrincipal LoginUser loginUser,
                                                    @Valid @RequestBody DeviceTokenRequest request) {
        String tokenValue = request.getToken().trim();
        ManagerDeviceToken t = repository.findByToken(tokenValue).orElseGet(ManagerDeviceToken::new);
        t.setManagerId(loginUser.id());
        t.setToken(tokenValue);
        t.setPlatform(request.getPlatform());
        t.setActive(true);
        repository.saveAndFlush(t);
        return ApiResponse.ok("푸시 토큰이 등록되었습니다.", new DeviceTokenResponse(t.getId(), t.getPlatform(), t.isActive()));
    }

    @Operation(summary = "푸시 토큰 해제", description = "로그아웃·알림 끄기. 내 토큰이면 사용 중지(행은 보존), 없거나 남의 토큰이면 조용히 무시")
    @DeleteMapping
    @Transactional
    public ApiResponse<Void> deactivate(@AuthenticationPrincipal LoginUser loginUser, @RequestParam("token") String token) {
        repository.findByToken(token.trim())
                .filter(t -> t.getManagerId().equals(loginUser.id()))
                .ifPresent(t -> t.setActive(false));
        return ApiResponse.ok("푸시 토큰이 해제되었습니다.", null);
    }
}
