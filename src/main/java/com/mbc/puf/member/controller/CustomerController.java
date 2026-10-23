package com.mbc.puf.member.controller;

import com.mbc.puf.member.dto.PhoneCodeTiming;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.SessionLoginManager;
import com.mbc.puf.member.dto.AvailabilityResponse;
import com.mbc.puf.member.dto.CustomerLoginRequest;
import com.mbc.puf.member.dto.CustomerLoginResponse;
import com.mbc.puf.member.dto.CustomerProfileResponse;
import com.mbc.puf.member.dto.CustomerProfileUpdateRequest;
import com.mbc.puf.member.dto.CustomerSignupRequest;
import com.mbc.puf.member.dto.CustomerSignupResponse;
import com.mbc.puf.member.dto.DeviceTokenRequest;
import com.mbc.puf.member.dto.DeviceTokenResponse;
import com.mbc.puf.member.dto.PasswordChangeRequest;
import com.mbc.puf.member.dto.PhoneCodeSendRequest;
import com.mbc.puf.member.dto.PhoneCodeVerifyRequest;
import com.mbc.puf.member.dto.WithdrawRequest;
import com.mbc.puf.member.service.CustomerService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "고객 회원/마이페이지", description = "가입·로그인·중복확인·내 정보·푸시 토큰")
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final SessionLoginManager sessionLoginManager;

    public CustomerController(CustomerService customerService, SessionLoginManager sessionLoginManager) {
        this.customerService = customerService;
        this.sessionLoginManager = sessionLoginManager;
    }

    // ==================== 회원가입 (로그인 없이 사용) ====================

    @Operation(summary = "아이디 중복확인")
    @GetMapping("/check-login-id")
    public ApiResponse<AvailabilityResponse> checkLoginId(@RequestParam("loginId") String loginId) {
        return ApiResponse.ok(customerService.checkLoginId(loginId));
    }

    // 회원가입, 그리고 정보 수정 화면에서 같이 쓴다. 고객으로 로그인한 상태면 본인 계정은 비교에서 빠진다
    // (내 현재 번호를 그대로 둬도 "사용 가능"으로 나온다)
    @Operation(summary = "전화번호 중복확인 (고객 로그인 상태면 본인 번호는 제외)")
    @GetMapping("/check-phone")
    public ApiResponse<AvailabilityResponse> checkPhone(@RequestParam("phone") String phone,
                                                        @AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(customerService.checkPhone(phone, customerIdOrNull(loginUser)));
    }

    // 이메일은 선택 항목이라 입력했을 때만 중복 확인한다(별도 인증 없음)
    @Operation(summary = "이메일 중복확인 (고객 로그인 상태면 본인 이메일은 제외)")
    @GetMapping("/check-email")
    public ApiResponse<AvailabilityResponse> checkEmail(@RequestParam("email") String email,
                                                        @AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(customerService.checkEmail(email, customerIdOrNull(loginUser)));
    }

    // 비로그인이거나 관리자 세션이면 null (= 전체 계정과 비교)
    private Long customerIdOrNull(LoginUser loginUser) {
        return loginUser != null && !loginUser.isManager() ? loginUser.id() : null;
    }

    @Operation(summary = "가입용 전화번호 인증코드 발송")
    @PostMapping("/phone-code/send")
    public ApiResponse<PhoneCodeTiming> sendPhoneCode(@Valid @RequestBody PhoneCodeSendRequest request) {
        return ApiResponse.ok("인증코드를 발송했습니다.", customerService.sendSignupPhoneCode(request.getPhone()));
    }

    @Operation(summary = "가입용 전화번호 인증코드 확인")
    @PostMapping("/phone-code/verify")
    public ApiResponse<Void> verifyPhoneCode(@Valid @RequestBody PhoneCodeVerifyRequest request) {
        customerService.verifySignupPhoneCode(request.getPhone(), request.getCode());
        return ApiResponse.ok("전화번호 인증이 완료되었습니다.", null);
    }

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<CustomerSignupResponse>> signup(
            @Valid @RequestBody CustomerSignupRequest request) {
        CustomerSignupResponse response = customerService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("회원가입이 완료되었습니다.", response));
    }

    // 로그인: 확인이 끝나면 세션에 로그인 상태를 남긴다 (응답 헤더의 Set-Cookie: JSESSIONID).
    // 웹푸시/앱 푸시 토큰은 로그인 후 POST /api/customers/me/device-tokens 로 따로 등록한다
    @Operation(summary = "고객 로그인 (세션)")
    @PostMapping("/login")
    public ApiResponse<CustomerLoginResponse> login(@Valid @RequestBody CustomerLoginRequest request,
                                                    HttpServletRequest httpRequest,
                                                    HttpServletResponse httpResponse) {
        CustomerLoginResponse result = customerService.login(request);
        sessionLoginManager.login(httpRequest, httpResponse, result.getId(), "CUSTOMER", "CUSTOMER");
        return ApiResponse.ok("로그인되었습니다.", result);
    }

    // ==================== 마이페이지 (고객 로그인 필요) ====================

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<CustomerProfileResponse> me(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(customerService.getMyProfile(loginUser.id()));
    }

    // 내 정보 수정 (이름·전화번호·이메일·긴급연락처·주소. 아이디·생년월일은 바꾸지 않는다)
    @Operation(summary = "내 정보 수정")
    @PutMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<CustomerProfileResponse> updateMe(@AuthenticationPrincipal LoginUser loginUser,
                                                         @Valid @RequestBody CustomerProfileUpdateRequest request) {
        return ApiResponse.ok("정보가 수정되었습니다.", customerService.updateProfile(loginUser.id(), request));
    }

    @Operation(summary = "비밀번호 변경")
    @PutMapping("/me/password")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal LoginUser loginUser,
                                            @Valid @RequestBody PasswordChangeRequest request) {
        customerService.changePassword(loginUser.id(), request);
        return ApiResponse.ok("비밀번호가 변경되었습니다.", null);
    }

    // 회원 탈퇴 (본인, 비밀번호 재확인). 차량·작성글·푸시 토큰도 함께 정리되고 세션도 바로 끊는다
    @Operation(summary = "회원 탈퇴")
    @PostMapping("/me/withdraw")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal LoginUser loginUser,
                                      @Valid @RequestBody WithdrawRequest request, HttpServletRequest httpRequest) {
        customerService.withdraw(loginUser.id(), request.getPassword());
        sessionLoginManager.logout(httpRequest);
        return ApiResponse.ok("탈퇴가 완료되었습니다.", null);
    }

    // ==================== 푸시 토큰 (웹/앱) ====================

    @Operation(summary = "내 푸시 토큰 목록")
    @GetMapping("/me/device-tokens")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<List<DeviceTokenResponse>> deviceTokens(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(customerService.getMyDeviceTokens(loginUser.id()));
    }

    // 등록 (이미 있는 토큰이면 다시 사용 상태로)
    @Operation(summary = "푸시 토큰 등록")
    @PostMapping("/me/device-tokens")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<DeviceTokenResponse> registerDeviceToken(@AuthenticationPrincipal LoginUser loginUser,
                                                                @Valid @RequestBody DeviceTokenRequest request) {
        return ApiResponse.ok("푸시 토큰이 등록되었습니다.", customerService.registerDeviceToken(loginUser.id(), request));
    }

    // 해제 (로그아웃·알림 끄기). 행은 남기고 사용만 중지
    @Operation(summary = "푸시 토큰 사용 중지")
    @DeleteMapping("/me/device-tokens")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Void> deactivateDeviceToken(@AuthenticationPrincipal LoginUser loginUser,
                                                   @RequestParam("token") String token) {
        customerService.deactivateDeviceToken(loginUser.id(), token);
        return ApiResponse.ok("푸시 토큰 사용을 중지했습니다.", null);
    }
}