package com.mbc.puf.member.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 고객 회원가입 요청. 전화번호는 필수이고 먼저 인증을 끝내야 한다. 이메일·주소는 선택 */
public class CustomerSignupRequest {

    // 공백 없는 ASCII 문자(영문·숫자·특수문자) 6~50자
    @NotBlank(message = "아이디는 필수입니다.")
    @Pattern(regexp = "^[\\x21-\\x7E]{6,50}$", message = "아이디는 공백 없이 영문·숫자·특수문자 6~50자로 입력해주세요.")
    private String loginId;

    // 공백 없는 ASCII 문자 8~64자. (BCrypt는 72바이트 초과 입력을 처리하지 못하므로 한글 등 멀티바이트는 제외)
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(regexp = "^[\\x21-\\x7E]{8,64}$", message = "비밀번호는 공백 없이 영문·숫자·특수문자 8~64자로 입력해주세요.")
    private String password;

    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    private String passwordConfirm;

    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @NotNull(message = "생년월일은 필수입니다.")
    @Past(message = "생년월일은 과거 날짜여야 합니다.")
    private LocalDate birthDate;

    // 선택 항목: 값이 있으면 형식·중복만 확인한다(별도 인증 없음)
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String email;

    // 하이픈은 있어도 없어도 된다 (010-1234-5678 / 01012345678). 저장할 때는 숫자만 남긴다.
    // 이 번호로 /api/customers/phone-code/verify 인증을 먼저 끝내야 가입된다
    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(regexp = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)")
    private String phone;

    // 선택 항목: 생략(null)하거나 빈 문자열이면 통과, 값이 있으면 전화번호 형식이어야 한다
    @Pattern(regexp = "^(\\d{2,3}-?\\d{3,4}-?\\d{4})?$", message = "긴급 연락처 형식이 올바르지 않습니다. (예: 010-1234-5678)")
    private String emergency;

    // 주소 API로 채워지는 값 (선택)
    @Size(max = 10, message = "우편번호는 10자 이하여야 합니다.")
    private String zipcode;

    @Size(max = 255, message = "도로명주소는 255자 이하여야 합니다.")
    private String roadAddress;

    // 직접 입력하는 값 (선택)
    @Size(max = 255, message = "상세주소는 255자 이하여야 합니다.")
    private String detailAddress;

    public CustomerSignupRequest() {
    }

    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPasswordConfirm() { return passwordConfirm; }
    public void setPasswordConfirm(String passwordConfirm) { this.passwordConfirm = passwordConfirm; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmergency() { return emergency; }
    public void setEmergency(String emergency) { this.emergency = emergency; }

    public String getZipcode() { return zipcode; }
    public void setZipcode(String zipcode) { this.zipcode = zipcode; }

    public String getRoadAddress() { return roadAddress; }
    public void setRoadAddress(String roadAddress) { this.roadAddress = roadAddress; }

    public String getDetailAddress() { return detailAddress; }
    public void setDetailAddress(String detailAddress) { this.detailAddress = detailAddress; }
}