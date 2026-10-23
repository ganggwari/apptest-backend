package com.mbc.puf.member.controller;

import com.mbc.puf.member.dto.PhoneCodeTiming;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.SessionLoginManager;
import com.mbc.puf.member.dto.AvailabilityResponse;
import com.mbc.puf.member.dto.EmployeeCheckResult;
import com.mbc.puf.member.dto.ManagerEmployeeCheckRequest;
import com.mbc.puf.member.dto.ManagerEmployeeCheckResponse;
import com.mbc.puf.member.dto.ManagerLoginRequest;
import com.mbc.puf.member.dto.ManagerLoginResponse;
import com.mbc.puf.member.dto.ManagerProfileResponse;
import com.mbc.puf.member.dto.ManagerProfileUpdateRequest;
import com.mbc.puf.member.dto.ManagerSignupRequest;
import com.mbc.puf.member.dto.ManagerSignupResponse;
import com.mbc.puf.member.dto.PasswordChangeRequest;
import com.mbc.puf.member.dto.VerifyCodeRequest;
import com.mbc.puf.member.dto.WithdrawRequest;
import com.mbc.puf.member.service.ManagerService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/** 관리자 본인 계정: 로그인, 가입(직원 대조 + 문자 인증), 마이페이지(MP-03) */
@RestController
@RequestMapping("/api/managers")
public class ManagerController {

    // 직원 대조에 성공한 employee.id 는 DB 에 남기지 않고 세션에만 잠깐 둔다 (중단되면 처음부터 다시)
    private static final String SIGNUP_EMPLOYEE_ID = "SIGNUP_EMPLOYEE_ID";

    private final ManagerService managerService;
    private final SessionLoginManager sessionLoginManager;

    public ManagerController(ManagerService managerService, SessionLoginManager sessionLoginManager) {
        this.managerService = managerService;
        this.sessionLoginManager = sessionLoginManager;
    }

    // 로그인. 응답의 regions 가 담당 지역 목록(조회·조작 범위)
    @PostMapping("/login")
    public ApiResponse<ManagerLoginResponse> login(@Valid @RequestBody ManagerLoginRequest request,
                                                   HttpServletRequest httpRequest,
                                                   HttpServletResponse httpResponse) {
        ManagerLoginResponse result = managerService.login(request);
        sessionLoginManager.login(httpRequest, httpResponse, result.getId(), "MANAGER", result.getRole().name());
        return ApiResponse.ok("로그인되었습니다.", result);
    }

    // ==================== 마이페이지 (MP-03, 관리자 로그인 필요) ====================

    @GetMapping("/me")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ApiResponse<ManagerProfileResponse> me(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(managerService.getMyProfile(loginUser.id()));
    }

    // 전화번호·이메일 수정 (이름은 직원 명단 값이라 수정 불가)
    @PutMapping("/me")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ApiResponse<ManagerProfileResponse> updateMe(@AuthenticationPrincipal LoginUser loginUser,
                                                        @Valid @RequestBody ManagerProfileUpdateRequest request) {
        return ApiResponse.ok("정보가 수정되었습니다.", managerService.updateMyProfile(loginUser.id(), request));
    }

    @PutMapping("/me/password")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal LoginUser loginUser,
                                            @Valid @RequestBody PasswordChangeRequest request) {
        managerService.changePassword(loginUser.id(), request);
        return ApiResponse.ok("비밀번호가 변경되었습니다.", null);
    }

    // 본인 탈퇴 (비밀번호 재확인). 세션도 바로 끊는다
    @PostMapping("/me/withdraw")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal LoginUser loginUser,
                                      @Valid @RequestBody WithdrawRequest request, HttpServletRequest httpRequest) {
        managerService.withdraw(loginUser.id(), request.getPassword());
        sessionLoginManager.logout(httpRequest);
        return ApiResponse.ok("탈퇴가 완료되었습니다.", null);
    }

    // ==================== 회원가입 (로그인 없이 사용) ====================

    @GetMapping("/check-login-id")
    public ApiResponse<AvailabilityResponse> checkLoginId(@RequestParam("loginId") String loginId) {
        return ApiResponse.ok(managerService.checkLoginId(loginId));
    }

    // 1단계: 이름·생년월일·전화번호를 직원 명단과 대조 → 명단의 번호로 인증코드 발송 (응답은 마스킹된 번호)
    @PostMapping("/signup/employee-check")
    public ApiResponse<ManagerEmployeeCheckResponse> checkEmployee(
            @Valid @RequestBody ManagerEmployeeCheckRequest request, HttpServletRequest httpRequest) {
        EmployeeCheckResult result = managerService.checkEmployeeAndSendCode(request);
        httpRequest.getSession(true).setAttribute(SIGNUP_EMPLOYEE_ID, result.employeeId());
        return ApiResponse.ok(result.maskedPhone() + " 번호로 인증코드를 보냈습니다.",
                new ManagerEmployeeCheckResponse(result.maskedPhone(), result.timing().expiresInSeconds(),
                        result.timing().resendAfterSeconds()));
    }

    @PostMapping("/signup/phone-code/resend")
    public ApiResponse<PhoneCodeTiming> resendPhoneCode(HttpServletRequest httpRequest) {
        return ApiResponse.ok("인증코드를 다시 발송했습니다.",
                managerService.resendSignupCode(requireSignupEmployeeId(httpRequest)));
    }

    // 2단계: 인증코드 확인
    @PostMapping("/signup/phone-code/verify")
    public ApiResponse<Void> verifyPhoneCode(@Valid @RequestBody VerifyCodeRequest request,
                                             HttpServletRequest httpRequest) {
        managerService.verifySignupCode(requireSignupEmployeeId(httpRequest), request.getCode());
        return ApiResponse.ok("전화번호 인증이 완료되었습니다.", null);
    }

    // 3단계: 아이디/비밀번호 → 승인대기(PENDING)로 생성, 일정 시간 뒤 자동 승인
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<ManagerSignupResponse>> signup(
            @Valid @RequestBody ManagerSignupRequest request, HttpServletRequest httpRequest) {
        ManagerSignupResponse response = managerService.signup(requireSignupEmployeeId(httpRequest), request);
        httpRequest.getSession().removeAttribute(SIGNUP_EMPLOYEE_ID);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("가입 신청이 완료되었습니다. 잠시 후 자동으로 승인됩니다.", response));
    }

    private Long requireSignupEmployeeId(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        Object value = (session == null) ? null : session.getAttribute(SIGNUP_EMPLOYEE_ID);
        if (value instanceof Long employeeId) {
            return employeeId;
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST,
                "가입 진행 정보가 없거나 만료되었습니다. 직원 확인부터 다시 진행해주세요.");
    }
}