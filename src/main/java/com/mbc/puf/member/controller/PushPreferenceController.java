package com.mbc.puf.member.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.member.repository.CustomerRepository;
import com.mbc.puf.member.repository.ManagerRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;

/**
 * 푸시 알림 사용 여부(끈 상태). 브라우저 알림 권한과 별개로, 사용자가 앱에서 직접 푸시를 끈 계정은
 * 앱을 지웠다 다시 깔거나 기기를 바꿔도 로그인 때 자동으로 다시 켜지지 않아야 하므로 서버에 저장한다.
 */
@Tag(name = "푸시 알림 설정", description = "푸시를 직접 끈 상태를 계정에 저장")
@RestController
@RequestMapping("/api")
public class PushPreferenceController {

    public record PushPreference(boolean optOut) {
    }

    public record PushPreferenceRequest(Boolean optOut) {
    }

    private final CustomerRepository customers;
    private final ManagerRepository managers;

    public PushPreferenceController(CustomerRepository customers, ManagerRepository managers) {
        this.customers = customers;
        this.managers = managers;
    }

    @Operation(summary = "고객 푸시 끈 상태 조회")
    @GetMapping("/customers/me/push-preference")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<PushPreference> customerPreference(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(new PushPreference(customers.findById(user.id())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다.")).isPushOptOut()));
    }

    @Operation(summary = "고객 푸시 끈 상태 저장")
    @PutMapping("/customers/me/push-preference")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public ApiResponse<PushPreference> updateCustomerPreference(@AuthenticationPrincipal LoginUser user,
                                                                @RequestBody PushPreferenceRequest request) {
        if (request.optOut() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "optOut 값이 필요합니다.");
        }
        var customer = customers.findById(user.id())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다."));
        customer.setPushOptOut(request.optOut());
        return ApiResponse.ok(new PushPreference(customer.isPushOptOut()));
    }

    @Operation(summary = "관리자 푸시 끈 상태 조회")
    @GetMapping("/managers/me/push-preference")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ApiResponse<PushPreference> managerPreference(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(new PushPreference(managers.findById(user.id())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "관리자 정보를 찾을 수 없습니다.")).isPushOptOut()));
    }

    @Operation(summary = "관리자 푸시 끈 상태 저장")
    @PutMapping("/managers/me/push-preference")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    @Transactional
    public ApiResponse<PushPreference> updateManagerPreference(@AuthenticationPrincipal LoginUser user,
                                                               @RequestBody PushPreferenceRequest request) {
        if (request.optOut() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "optOut 값이 필요합니다.");
        }
        var manager = managers.findById(user.id())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "관리자 정보를 찾을 수 없습니다."));
        manager.setPushOptOut(request.optOut());
        return ApiResponse.ok(new PushPreference(manager.isPushOptOut()));
    }
}
