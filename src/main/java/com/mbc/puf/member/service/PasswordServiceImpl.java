package com.mbc.puf.member.service;

import com.mbc.puf.member.dto.PhoneCodeTiming;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.ManagerStatus;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.util.PhoneNumber;
import com.mbc.puf.member.entity.Customer;
import com.mbc.puf.member.entity.Employee;
import com.mbc.puf.member.entity.Manager;
import com.mbc.puf.member.repository.CustomerRepository;
import com.mbc.puf.member.repository.EmployeeRepository;
import com.mbc.puf.member.repository.ManagerRepository;
import com.mbc.puf.member.service.PhoneVerificationService.ResetTarget;

@Service
public class PasswordServiceImpl implements PasswordService {

	// 아이디가 없는 경우와 번호가 틀린 경우를 구분하지 않는다 (가입 여부를 알아내는 데 쓰이지 않도록)
	private static final String NOT_MATCHED = "아이디 또는 전화번호가 일치하지 않습니다.";

	private final CustomerRepository customerRepository;
	private final ManagerRepository managerRepository;
	private final EmployeeRepository employeeRepository;
	private final PhoneVerificationService phoneVerificationService;
	private final PasswordEncoder passwordEncoder;

	public PasswordServiceImpl(CustomerRepository customerRepository, ManagerRepository managerRepository,
			EmployeeRepository employeeRepository, PhoneVerificationService phoneVerificationService,
			PasswordEncoder passwordEncoder) {
		this.customerRepository = customerRepository;
		this.managerRepository = managerRepository;
		this.employeeRepository = employeeRepository;
		this.phoneVerificationService = phoneVerificationService;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public PhoneCodeTiming codeTiming() {
		return phoneVerificationService.timing();
	}

	// ==================== 고객 ====================

	@Override
	@Transactional
	public Long requestForCustomer(String loginId, String phone) {
		// 탈퇴하지 않은 계정 중 아이디와 가입 전화번호가 모두 맞아야 한다
		Customer customer = customerRepository.findByLoginIdAndWithdrawnFalse(loginId.trim()).orElse(null);
		String target = PhoneNumber.normalize(phone);
		if (customer == null || !target.equals(customer.getPhone())) {
			throw new BusinessException(HttpStatus.NOT_FOUND, NOT_MATCHED);
		}
		phoneVerificationService.sendResetCode(ResetTarget.customer(customer.getId()), target);
		return customer.getId();   // 컨트롤러가 세션에 임시로 보관
	}

	@Override
	@Transactional
	public void verifyForCustomer(Long customerId, String code) {
		phoneVerificationService.verifyResetCode(ResetTarget.customer(customerId), code);
	}

	@Override
	@Transactional
	public void confirmForCustomer(Long customerId, String newPassword, String newPasswordConfirm) {
		phoneVerificationService.assertResetVerified(ResetTarget.customer(customerId));
		assertSame(newPassword, newPasswordConfirm);
		customerRepository.findById(customerId).ifPresent(c -> c.setPassword(passwordEncoder.encode(newPassword)));
	}

	// ==================== 관리자 ====================

	@Override
	@Transactional
	public Long requestForManager(String loginId, String phone) {
		// 활성 계정이고, 직원 명단(employee)의 전화번호와 맞아야 한다
		Manager manager = managerRepository.findByLoginId(loginId.trim()).orElse(null);
		String target = PhoneNumber.normalize(phone);
		String employeePhone = (manager == null) ? null
				: employeeRepository.findById(manager.getEmployeeId()).map(Employee::getPhone)
						.map(PhoneNumber::normalize).orElse(null);
		if (manager == null || manager.getStatus() != ManagerStatus.ACTIVE || !target.equals(employeePhone)) {
			throw new BusinessException(HttpStatus.NOT_FOUND, NOT_MATCHED);
		}
		phoneVerificationService.sendResetCode(ResetTarget.manager(manager.getId()), target);
		return manager.getId();
	}

	@Override
	@Transactional
	public void verifyForManager(Long managerId, String code) {
		phoneVerificationService.verifyResetCode(ResetTarget.manager(managerId), code);
	}

	@Override
	@Transactional
	public void confirmForManager(Long managerId, String newPassword, String newPasswordConfirm) {
		phoneVerificationService.assertResetVerified(ResetTarget.manager(managerId));
		assertSame(newPassword, newPasswordConfirm);
		managerRepository.findById(managerId).ifPresent(m -> m.setPassword(passwordEncoder.encode(newPassword)));
	}

	// ---- 내부 메서드 ----

	private void assertSame(String newPassword, String newPasswordConfirm) {
		if (!newPassword.equals(newPasswordConfirm)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "비밀번호와 비밀번호 확인이 일치하지 않습니다.");
		}
	}
}