package com.mbc.puf.member.service;

import com.mbc.puf.member.dto.PhoneCodeTiming;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.ManagerRole;
import com.mbc.puf.common.enums.ManagerStatus;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.util.PhoneNumber;
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
import com.mbc.puf.member.dto.ShiftResponse;
import com.mbc.puf.member.entity.Employee;
import com.mbc.puf.member.entity.Manager;
import com.mbc.puf.member.repository.EmployeeRepository;
import com.mbc.puf.member.repository.ManagerRegionRepository;
import com.mbc.puf.member.repository.ManagerRepository;
import com.mbc.puf.member.repository.ShiftRepository;
import com.mbc.puf.region.entity.Region;
import com.mbc.puf.region.repository.RegionRepository;

@Service
public class ManagerServiceImpl implements ManagerService {

    private final ManagerRepository managerRepository;
    private final EmployeeRepository employeeRepository;
    private final ManagerRegionRepository managerRegionRepository;
    private final RegionRepository regionRepository;
    private final ShiftRepository shiftRepository;
    private final PhoneVerificationService phoneVerificationService;
    private final PasswordEncoder passwordEncoder;

    public ManagerServiceImpl(ManagerRepository managerRepository, EmployeeRepository employeeRepository,
                              ManagerRegionRepository managerRegionRepository, RegionRepository regionRepository,
                              ShiftRepository shiftRepository, PhoneVerificationService phoneVerificationService,
                              PasswordEncoder passwordEncoder) {
        this.managerRepository = managerRepository;
        this.employeeRepository = employeeRepository;
        this.managerRegionRepository = managerRegionRepository;
        this.regionRepository = regionRepository;
        this.shiftRepository = shiftRepository;
        this.phoneVerificationService = phoneVerificationService;
        this.passwordEncoder = passwordEncoder;
    }

    // ==================== 로그인 ====================

    @Override
    @Transactional(readOnly = true)
    public ManagerLoginResponse login(ManagerLoginRequest request) {
        Manager manager = managerRepository.findByLoginId(request.getLoginId().trim()).orElse(null);

        if (manager == null || !passwordEncoder.matches(request.getPassword(), manager.getPassword())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        if (manager.getStatus() == ManagerStatus.PENDING) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "승인 대기 중입니다. 잠시 후 다시 로그인해 주세요.");
        }
        if (manager.getStatus() != ManagerStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "사용할 수 없는 계정입니다.");
        }

        String name = employeeRepository.findById(manager.getEmployeeId()).map(Employee::getName).orElse(null);
        return new ManagerLoginResponse(manager.getId(), manager.getLoginId(), name, manager.getRole(),
                getRegions(manager.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityResponse checkLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "아이디를 입력해 주세요.");
        }
        return new AvailabilityResponse(!managerRepository.existsByLoginId(loginId.trim()));
    }

    // ==================== 가입 ====================

    // 이름·생년월일·전화번호가 직원 명단과 맞으면, 명단에 있는 번호로 인증코드를 보낸다
    @Override
    @Transactional
    public EmployeeCheckResult checkEmployeeAndSendCode(ManagerEmployeeCheckRequest request) {
        Employee employee = employeeRepository.findByIdentity(
                request.getName().trim(), request.getBirthDate(), PhoneNumber.normalize(request.getPhone()))
                .stream().findFirst().orElse(null);

        if (employee == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "일치하는 직원 정보가 없습니다. 이름, 생년월일, 전화번호를 확인해 주세요.");
        }
        assertNoActiveAccount(employee.getId());

        String phone = PhoneNumber.normalize(employee.getPhone());
        phoneVerificationService.sendSignupCode(phone);
        return new EmployeeCheckResult(employee.getId(), PhoneNumber.mask(phone), phoneVerificationService.timing());
    }

    @Override
    @Transactional
    public PhoneCodeTiming resendSignupCode(Long employeeId) {
        Employee employee = requireEmployee(employeeId);
        assertNoActiveAccount(employeeId);
        phoneVerificationService.sendSignupCode(PhoneNumber.normalize(employee.getPhone()));
        return phoneVerificationService.timing();
    }

    @Override
    @Transactional
    public void verifySignupCode(Long employeeId, String code) {
        Employee employee = requireEmployee(employeeId);
        phoneVerificationService.verifySignupCode(PhoneNumber.normalize(employee.getPhone()), code);
    }

    @Override
    @Transactional
    public ManagerSignupResponse signup(Long employeeId, ManagerSignupRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }

        Employee employee = requireEmployee(employeeId);
        phoneVerificationService.assertSignupVerified(PhoneNumber.normalize(employee.getPhone()));
        assertNoActiveAccount(employeeId);

        String loginId = request.getLoginId().trim();
        if (managerRepository.existsByLoginId(loginId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다.");
        }

        // 담당 지역·근무조는 가입 때 정하지 않는다 (MP-12 회원관리에서 배정)
        Manager manager = new Manager();
        manager.setEmployeeId(employeeId);
        manager.setLoginId(loginId);
        manager.setPassword(passwordEncoder.encode(request.getPassword()));
        manager.setRole(ManagerRole.REGIONAL_ADMIN);
        manager.setStatus(ManagerStatus.PENDING);   // 일정 시간 뒤 ManagerAutoApproveScheduler 가 ACTIVE 로

        try {
            managerRepository.saveAndFlush(manager);   // flush 해야 유니크 위반이 여기서 바로 터진다
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 사용 중인 아이디이거나 이미 가입된 직원입니다.");
        }
        return new ManagerSignupResponse(manager.getId(), manager.getLoginId(), manager.getStatus());
    }

    // ==================== 마이페이지 (MP-03) ====================

    @Override
    @Transactional(readOnly = true)
    public ManagerProfileResponse getMyProfile(Long managerId) {
        return toProfile(requireActiveManager(managerId));
    }

    // 전화번호·이메일은 직원 명단(employee)의 값을 바꾼다. 이름은 바꿀 수 없다
    @Override
    @Transactional
    public ManagerProfileResponse updateMyProfile(Long managerId, ManagerProfileUpdateRequest request) {
        Manager manager = requireActiveManager(managerId);
        Employee employee = employeeRepository.findById(manager.getEmployeeId()).orElseThrow();
        employee.setPhone(PhoneNumber.normalize(request.getPhone()));
        employee.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        try {
            employeeRepository.flush();   // employee.email UNIQUE
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 다른 직원이 사용 중인 이메일입니다.");
        }
        return toProfile(manager);
    }

    @Override
    @Transactional
    public void changePassword(Long managerId, PasswordChangeRequest request) {
        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "새 비밀번호와 새 비밀번호 확인이 일치하지 않습니다.");
        }
        Manager manager = requireActiveManager(managerId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), manager.getPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다.");
        }
        manager.setPassword(passwordEncoder.encode(request.getNewPassword()));
    }

    // 본인 탈퇴: 비밀번호 확인 후 status = WITHDRAWN (행 삭제 아님, 직원 명단은 그대로).
    // 담당 지역 행은 지운다(탈퇴한 관리자가 지역 담당으로 남지 않게)
    @Override
    @Transactional
    public void withdraw(Long managerId, String password) {
        Manager manager = requireActiveManager(managerId);
        if (!passwordEncoder.matches(password, manager.getPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "비밀번호가 올바르지 않습니다.");
        }
        if (managerRepository.withdraw(managerId) == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 탈퇴 처리된 계정입니다.");
        }
        managerRegionRepository.deleteByManagerId(managerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegionRef> getRegions(Long managerId) {
        List<Long> ids = managerRegionRepository.findRegionIds(managerId);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = regionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Region::getId, Region::getName, (a, b) -> a));
        return ids.stream().map(id -> new RegionRef(id, names.get(id))).toList();
    }

    // ---- 내부 메서드 ----

    private ManagerProfileResponse toProfile(Manager manager) {
        Employee employee = employeeRepository.findById(manager.getEmployeeId()).orElseThrow();
        ShiftResponse shift = manager.getShiftId() == null ? null
                : shiftRepository.findById(manager.getShiftId())
                        .map(s -> new ShiftResponse(s.getId(), s.getName(), s.getStartTime(), s.getEndTime()))
                        .orElse(null);
        return new ManagerProfileResponse(manager.getId(), manager.getLoginId(), employee.getName(),
                employee.getEmail(), employee.getPhone(), manager.getRole(), getRegions(manager.getId()), shift);
    }

    private Manager requireActiveManager(Long managerId) {
        Manager manager = managerRepository.findById(managerId).orElse(null);
        if (manager == null || manager.getStatus() != ManagerStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "유효하지 않은 계정입니다. 다시 로그인해 주세요.");
        }
        return manager;
    }

    private Employee requireEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId).orElseThrow(() -> new BusinessException(
                HttpStatus.BAD_REQUEST, "직원 정보를 찾을 수 없습니다. 처음부터 다시 진행해 주세요."));
    }

    private void assertNoActiveAccount(Long employeeId) {
        if (managerRepository.countByEmployeeIdAndStatusNot(employeeId, ManagerStatus.WITHDRAWN) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 가입되었거나 승인 대기 중인 직원입니다.");
        }
    }

}