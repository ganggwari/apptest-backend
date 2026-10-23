package com.mbc.puf.member.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.member.dto.PasswordResetConfirmRequest;
import com.mbc.puf.member.dto.PhoneCodeTiming;
import com.mbc.puf.member.dto.PasswordResetRequestRequest;
import com.mbc.puf.member.dto.PasswordResetVerifyRequest;
import com.mbc.puf.member.service.PasswordService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
public class PasswordResetController {

	private final PasswordService passwordService;
	private static final String CUSTOMER_RESET_ID = "PASSWORD_RESET_CUSTOMER_ID";
	private static final String MANAGER_RESET_ID = "PASSWORD_RESET_MANAGER_ID";

	public PasswordResetController(PasswordService passwordService) {
		super();
		this.passwordService = passwordService;
	}

	@PostMapping("/api/customers/password-reset/request")
	public ApiResponse<PhoneCodeTiming> requestForCustomer(@Valid @RequestBody PasswordResetRequestRequest request,
			HttpServletRequest httpRequest) {
		Long customerId = passwordService.requestForCustomer(request.getLoginId(), request.getPhone());
		httpRequest.getSession(true).setAttribute(CUSTOMER_RESET_ID, customerId);
		return ApiResponse.ok("인증코드를 발송했습니다.", passwordService.codeTiming());
	}

	@PostMapping("/api/managers/password-reset/request")
	public ApiResponse<PhoneCodeTiming> requestForManager(@Valid @RequestBody PasswordResetRequestRequest request,
			HttpServletRequest httpRequest) {
		Long managerId = passwordService.requestForManager(request.getLoginId(), request.getPhone());
		httpRequest.getSession(true).setAttribute(MANAGER_RESET_ID, managerId);
		return ApiResponse.ok("인증코드를 발송했습니다.", passwordService.codeTiming());
	}

	@PostMapping("/api/customers/password-reset/verify")
	public ApiResponse<Void> verifyForCustomer(@Valid @RequestBody PasswordResetVerifyRequest request,
			HttpServletRequest httpRequest) {
		Long customerId = requireResetId(httpRequest, CUSTOMER_RESET_ID);
		passwordService.verifyForCustomer(customerId, request.getCode());
		return ApiResponse.ok("전화번호 인증이 완료되었습니다.", null);
	}

	@PostMapping("/api/managers/password-reset/verify")
	public ApiResponse<Void> verifyForManager(@Valid @RequestBody PasswordResetVerifyRequest request,
			HttpServletRequest httpRequest) {
		Long managerId = requireResetId(httpRequest, MANAGER_RESET_ID);
		passwordService.verifyForManager(managerId, request.getCode());
		return ApiResponse.ok("전화번호 인증이 완료되었습니다.", null);
	}

	@PostMapping("/api/customers/password-reset/confirm")
	public ApiResponse<Void> confirmForCustomer(@Valid @RequestBody PasswordResetConfirmRequest request,
			HttpServletRequest httpRequest) {
		Long customerId = requireResetId(httpRequest, CUSTOMER_RESET_ID);
		passwordService.confirmForCustomer(customerId, request.getNewPassword(), request.getNewPasswordConfirm());
		return ApiResponse.ok("비밀번호 변경이 완료되었습니다.", null);
	}
	
	@PostMapping("/api/managers/password-reset/confirm")
	public ApiResponse<Void> confirmForManager(@Valid @RequestBody PasswordResetConfirmRequest request,
			HttpServletRequest httpRequest) {
		Long managerrId = requireResetId(httpRequest, MANAGER_RESET_ID);
		passwordService.confirmForManager(managerrId, request.getNewPassword(), request.getNewPasswordConfirm());
		return ApiResponse.ok("비밀번호 변경이 완료되었습니다.", null);
	}

	// ---- 내부 매서드 ----
	private Long requireResetId(HttpServletRequest httpRequest, String sessionKey) {
		HttpSession session = httpRequest.getSession(false); // 없는 세션을 새로 만들지 않는다
		Object value = (session == null) ? null : session.getAttribute(sessionKey);
		if (value instanceof Long id) {
			return id;
		}
		throw new BusinessException(HttpStatus.BAD_REQUEST, "인증 요청 정보가 없거나 만료되었습니다. 처음부터 다시 진행해주세요.");
	}
}