package com.mbc.puf.member.service;

import com.mbc.puf.member.dto.PhoneCodeTiming;
import java.util.List;

import com.mbc.puf.member.dto.AvailabilityResponse;
import com.mbc.puf.member.dto.EmployeeCheckResult;
import com.mbc.puf.member.dto.ManagerEmployeeCheckRequest;
import com.mbc.puf.member.dto.ManagerLoginRequest;
import com.mbc.puf.member.dto.ManagerLoginResponse;
import com.mbc.puf.member.dto.ManagerProfileResponse;
import com.mbc.puf.member.dto.ManagerProfileUpdateRequest;
import com.mbc.puf.member.dto.ManagerSignupRequest;
import com.mbc.puf.member.dto.ManagerSignupResponse;
import com.mbc.puf.member.dto.PasswordChangeRequest;
import com.mbc.puf.member.dto.RegionRef;

public interface ManagerService {

    ManagerLoginResponse login(ManagerLoginRequest request);
    AvailabilityResponse checkLoginId(String loginId);

    // 가입: 직원 대조 → 직원 명단 번호로 문자 인증 → 아이디/비밀번호 → 승인대기(일정 시간 뒤 자동 승인)
    EmployeeCheckResult checkEmployeeAndSendCode(ManagerEmployeeCheckRequest request);
    PhoneCodeTiming resendSignupCode(Long employeeId);
    void verifySignupCode(Long employeeId, String code);
    ManagerSignupResponse signup(Long employeeId, ManagerSignupRequest request);

    // 마이페이지 (MP-03)
    ManagerProfileResponse getMyProfile(Long managerId);
    ManagerProfileResponse updateMyProfile(Long managerId, ManagerProfileUpdateRequest request);
    void changePassword(Long managerId, PasswordChangeRequest request);
    void withdraw(Long managerId, String password);

    // 담당 지역 (id + 이름)
    List<RegionRef> getRegions(Long managerId);
}