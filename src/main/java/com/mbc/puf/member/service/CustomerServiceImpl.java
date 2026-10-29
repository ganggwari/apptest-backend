package com.mbc.puf.member.service;

import com.mbc.puf.member.dto.PhoneCodeTiming;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.board.repository.InquiryRepository;
import com.mbc.puf.car.repository.CarRepository;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.util.PhoneNumber;
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
import com.mbc.puf.member.entity.Customer;
import com.mbc.puf.member.entity.DeviceToken;
import com.mbc.puf.member.repository.CustomerRepository;
import com.mbc.puf.member.repository.DeviceTokenRepository;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CarRepository carRepository;
    private final InquiryRepository inquiryRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PhoneVerificationService phoneVerificationService;
    private final PasswordEncoder passwordEncoder;

    public CustomerServiceImpl(CustomerRepository customerRepository, CarRepository carRepository,
                               InquiryRepository inquiryRepository, DeviceTokenRepository deviceTokenRepository,
                               PhoneVerificationService phoneVerificationService, PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.carRepository = carRepository;
        this.inquiryRepository = inquiryRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.phoneVerificationService = phoneVerificationService;
        this.passwordEncoder = passwordEncoder;
    }

    // ==================== 가입 전 확인 ====================

    @Override
    @Transactional(readOnly = true)
    public AvailabilityResponse checkLoginId(String loginId) {
        String target = requireText(loginId, "아이디를 입력해 주세요.");
        return new AvailabilityResponse(!customerRepository.existsByLoginIdAndWithdrawnFalse(target));
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityResponse checkPhone(String phone) {
        return checkPhone(phone, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityResponse checkPhone(String phone, Long excludeCustomerId) {
        String target = PhoneNumber.normalize(requireText(phone, "전화번호를 입력해 주세요."));
        boolean taken = excludeCustomerId == null
                ? customerRepository.existsByPhoneAndWithdrawnFalse(target)
                : customerRepository.existsByPhoneAndWithdrawnFalseAndIdNot(target, excludeCustomerId);
        return new AvailabilityResponse(!taken);
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityResponse checkEmail(String email) {
        return checkEmail(email, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityResponse checkEmail(String email, Long excludeCustomerId) {
        String target = normalizeEmail(requireText(email, "이메일을 입력해 주세요."));
        boolean taken = excludeCustomerId == null
                ? customerRepository.existsByEmailAndWithdrawnFalse(target)
                : customerRepository.existsByEmailAndWithdrawnFalseAndIdNot(target, excludeCustomerId);
        return new AvailabilityResponse(!taken);
    }

    @Override
    @Transactional
    public PhoneCodeTiming sendSignupPhoneCode(String phone) {
        String target = PhoneNumber.normalize(phone);
        if (customerRepository.existsByPhoneAndWithdrawnFalse(target)) {
            throw conflict("이미 가입된 전화번호입니다.");
        }
        phoneVerificationService.sendSignupCode(target);
        return phoneVerificationService.timing();
    }

    @Override
    public void verifySignupPhoneCode(String phone, String code) {
        phoneVerificationService.verifySignupCode(PhoneNumber.normalize(phone), code);
    }

    // ==================== 가입 / 로그인 ====================

    @Override
    @Transactional
    public CustomerSignupResponse signup(CustomerSignupRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }

        String loginId = request.getLoginId().trim();
        String phone = PhoneNumber.normalize(request.getPhone());
        String email = normalizeEmail(blankToNull(request.getEmail()));

        phoneVerificationService.assertSignupVerified(phone);

        // 중복은 탈퇴하지 않은 계정끼리만 본다 → 탈퇴한 사람의 아이디·번호로는 다시 가입할 수 있다
        if (customerRepository.existsByLoginIdAndWithdrawnFalse(loginId)) {
            throw conflict("이미 사용 중인 아이디입니다.");
        }
        if (customerRepository.existsByPhoneAndWithdrawnFalse(phone)) {
            throw conflict("이미 가입된 전화번호입니다.");
        }
        if (email != null && customerRepository.existsByEmailAndWithdrawnFalse(email)) {
            throw conflict("이미 사용 중인 이메일입니다.");
        }

        Customer customer = new Customer();
        customer.setLoginId(loginId);
        customer.setPassword(passwordEncoder.encode(request.getPassword()));
        customer.setName(request.getName().trim());
        customer.setBirthDate(request.getBirthDate());
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setEmergency(PhoneNumber.normalize(request.getEmergency()));
        customer.setZipcode(blankToNull(request.getZipcode()));
        customer.setRoadAddress(blankToNull(request.getRoadAddress()));
        customer.setDetailAddress(blankToNull(request.getDetailAddress()));

        try {
            customerRepository.saveAndFlush(customer);   // flush 해야 유니크 위반이 여기서 바로 터진다
        } catch (DataIntegrityViolationException e) {
            throw conflict("이미 사용 중인 아이디·전화번호·이메일입니다.");
        }

        return new CustomerSignupResponse(customer.getId(), customer.getLoginId(), customer.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerLoginResponse login(CustomerLoginRequest request) {
        // 탈퇴한 계정은 찾지 않는다 (같은 아이디로 새로 가입한 계정이 있을 수 있음)
        Customer customer = customerRepository.findByLoginIdAndWithdrawnFalse(request.getLoginId().trim()).orElse(null);

        if (customer == null || !passwordEncoder.matches(request.getPassword(), customer.getPassword())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        return new CustomerLoginResponse(customer.getId(), customer.getLoginId(), customer.getName());
    }

    // ==================== 마이페이지 ====================

    @Override
    @Transactional(readOnly = true)
    public CustomerProfileResponse getMyProfile(Long customerId) {
        return toProfileResponse(requireActiveCustomer(customerId));
    }

    @Override
    @Transactional
    public CustomerProfileResponse updateProfile(Long customerId, CustomerProfileUpdateRequest request) {
        return applyProfile(requireActiveCustomer(customerId), request);
    }

    // 본인 수정 규칙 (전화번호·이메일 중복은 다른 활성 계정과만 비교)
    private CustomerProfileResponse applyProfile(Customer customer, CustomerProfileUpdateRequest request) {
        Long customerId = customer.getId();

        String phone = PhoneNumber.normalize(request.getPhone());
        String email = normalizeEmail(blankToNull(request.getEmail()));
        // TODO(확인 필요): 전화번호를 바꿀 때 새 번호로 재인증을 받을지. 지금은 중복만 막는다
        if (customerRepository.existsByPhoneAndWithdrawnFalseAndIdNot(phone, customerId)) {
            throw conflict("이미 다른 계정에서 사용 중인 전화번호입니다.");
        }
        if (email != null && customerRepository.existsByEmailAndWithdrawnFalseAndIdNot(email, customerId)) {
            throw conflict("이미 다른 계정에서 사용 중인 이메일입니다.");
        }

        customer.setName(request.getName().trim());
        customer.setPhone(phone);
        customer.setEmail(email);
        customer.setEmergency(PhoneNumber.normalize(request.getEmergency()));
        customer.setZipcode(blankToNull(request.getZipcode()));
        customer.setRoadAddress(blankToNull(request.getRoadAddress()));
        customer.setDetailAddress(blankToNull(request.getDetailAddress()));
        try {
            customerRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw conflict("이미 다른 계정에서 사용 중인 전화번호 또는 이메일입니다.");
        }
        return toProfileResponse(customer);
    }

    @Override
    @Transactional
    public void changePassword(Long customerId, PasswordChangeRequest request) {
        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "새 비밀번호와 새 비밀번호 확인이 일치하지 않습니다.");
        }
        Customer customer = requireActiveCustomer(customerId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), customer.getPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다.");
        }
        customer.setPassword(passwordEncoder.encode(request.getNewPassword()));
    }

    // 탈퇴: 비밀번호 확인 → 한 트랜잭션으로 계정 탈퇴 + 내 차량·내가 쓴 원글 소프트 삭제 + 푸시 토큰 사용 중지.
    // 이후 조회와 알림 매칭에서 모두 빠지고, 알림·번호판 인식 로그는 그대로 남는다
    @Override
    @Transactional
    public void withdraw(Long customerId, String password) {
        Customer customer = requireActiveCustomer(customerId);
        if (!passwordEncoder.matches(password, customer.getPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "비밀번호가 올바르지 않습니다.");
        }

        withdrawAll(customer);
    }

    // 계정 탈퇴 + 내 차량·내가 쓴 원글 소프트 삭제 + 푸시 토큰 사용 중지 (호출하는 쪽 트랜잭션 안에서)
    private void withdrawAll(Customer customer) {
        Long customerId = customer.getId();
        // 아래 일괄 UPDATE 들은 실행 전에 이 변경을 먼저 DB에 반영(flush)하므로 순서는 이대로 둔다
        customer.setWithdrawn(true);
        carRepository.softDeleteAllByCustomerId(customerId);
        inquiryRepository.softDeleteAllByCustomerId(customerId);
        deviceTokenRepository.deactivateAllByCustomerId(customerId);
    }

    // ==================== 푸시 토큰 ====================

    // 같은 토큰이 이미 있으면 새로 만들지 않고 주인·플랫폼을 맞춘 뒤 다시 켠다
    // (로그아웃 후 재로그인, 또는 같은 기기에서 다른 계정으로 로그인한 경우)
    @Override
    @Transactional
    public DeviceTokenResponse registerDeviceToken(Long customerId, DeviceTokenRequest request) {
        requireActiveCustomer(customerId);
        String tokenValue = request.getToken().trim();

        DeviceToken token = deviceTokenRepository.findByToken(tokenValue).orElseGet(DeviceToken::new);
        token.setCustomerId(customerId);
        token.setToken(tokenValue);
        token.setPlatform(request.getPlatform());
        token.setActive(true);
        try {
            deviceTokenRepository.saveAndFlush(token);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 토큰을 등록한 경우: 이미 저장된 행을 다시 켠다
            throw conflict("이미 등록 중인 토큰입니다. 다시 시도해 주세요.");
        }
        return toTokenResponse(token);
    }

    // 로그아웃·알림 끄기: 내 토큰이면 사용 중지(행은 보존). 없거나 남의 토큰이면 조용히 무시
    @Override
    @Transactional
    public void deactivateDeviceToken(Long customerId, String token) {
        deviceTokenRepository.findByToken(token.trim())
                .filter(t -> t.getCustomerId().equals(customerId))
                .ifPresent(t -> t.setActive(false));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceTokenResponse> getMyDeviceTokens(Long customerId) {
        return deviceTokenRepository.findByCustomerIdAndActiveTrueOrderByIdAsc(customerId).stream()
                .map(this::toTokenResponse).toList();
    }

    // ---- 내부 메서드 ----

    private Customer requireActiveCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null || customer.isWithdrawn()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "유효하지 않은 계정입니다. 다시 로그인해 주세요.");
        }
        return customer;
    }

    private Customer requireExistingCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 고객입니다. id=" + customerId));
    }

    private CustomerProfileResponse toProfileResponse(Customer customer) {
        return new CustomerProfileResponse(customer.getId(), customer.getLoginId(), customer.getName(),
                customer.getBirthDate(), customer.getEmail(), customer.getPhone(), customer.getEmergency(),
                customer.getZipcode(), customer.getRoadAddress(), customer.getDetailAddress(),
                customer.getCreatedAt());
    }

    private DeviceTokenResponse toTokenResponse(DeviceToken token) {
        return new DeviceTokenResponse(token.getId(), token.getPlatform(), token.isActive());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }
}