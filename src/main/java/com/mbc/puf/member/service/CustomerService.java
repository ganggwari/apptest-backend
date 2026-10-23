package com.mbc.puf.member.service;

import com.mbc.puf.member.dto.PhoneCodeTiming;
import java.util.List;

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

public interface CustomerService {

    // 가입 전 중복 확인 (탈퇴하지 않은 계정 기준)
    AvailabilityResponse checkLoginId(String loginId);
    AvailabilityResponse checkPhone(String phone);
    AvailabilityResponse checkEmail(String email);

    // 로그인한 고객이 정보 수정 중일 때: 본인 계정은 중복 비교에서 제외한다 (excludeCustomerId 가 null 이면 위와 동일)
    AvailabilityResponse checkPhone(String phone, Long excludeCustomerId);
    AvailabilityResponse checkEmail(String email, Long excludeCustomerId);

    // 가입용 전화번호 인증
    PhoneCodeTiming sendSignupPhoneCode(String phone);
    void verifySignupPhoneCode(String phone, String code);

    CustomerSignupResponse signup(CustomerSignupRequest request);
    CustomerLoginResponse login(CustomerLoginRequest request);

    // 마이페이지
    CustomerProfileResponse getMyProfile(Long customerId);
    CustomerProfileResponse updateProfile(Long customerId, CustomerProfileUpdateRequest request);
    void changePassword(Long customerId, PasswordChangeRequest request);

    // 탈퇴: 비밀번호 확인 후 계정·차량·작성글·푸시 토큰을 한 트랜잭션으로 정리
    void withdraw(Long customerId, String password);

    // 푸시 토큰 (웹/앱)
    DeviceTokenResponse registerDeviceToken(Long customerId, DeviceTokenRequest request);
    void deactivateDeviceToken(Long customerId, String token);
    List<DeviceTokenResponse> getMyDeviceTokens(Long customerId);
}