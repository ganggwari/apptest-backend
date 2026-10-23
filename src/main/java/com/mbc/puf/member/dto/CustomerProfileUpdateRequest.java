package com.mbc.puf.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 고객 마이페이지 정보 수정 요청. 아이디·생년월일은 바꾸지 않는다.
 * 전화번호·이메일은 다른 활성 계정과 겹치면 409.
 */
public class CustomerProfileUpdateRequest {

    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(regexp = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)")
    private String phone;

    // 선택 항목: 생략하거나 빈 문자열이면 이메일을 지운다
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String email;

    // 선택 항목: 생략(null)하거나 빈 문자열이면 통과
    @Pattern(regexp = "^(\\d{2,3}-?\\d{3,4}-?\\d{4})?$", message = "긴급 연락처 형식이 올바르지 않습니다. (예: 010-1234-5678)")
    private String emergency;

    @Size(max = 10, message = "우편번호는 10자 이하여야 합니다.")
    private String zipcode;

    @Size(max = 255, message = "도로명주소는 255자 이하여야 합니다.")
    private String roadAddress;

    @Size(max = 255, message = "상세주소는 255자 이하여야 합니다.")
    private String detailAddress;

    public CustomerProfileUpdateRequest() {
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getEmergency() { return emergency; }
    public void setEmergency(String emergency) { this.emergency = emergency; }

    public String getZipcode() { return zipcode; }
    public void setZipcode(String zipcode) { this.zipcode = zipcode; }

    public String getRoadAddress() { return roadAddress; }
    public void setRoadAddress(String roadAddress) { this.roadAddress = roadAddress; }

    public String getDetailAddress() { return detailAddress; }
    public void setDetailAddress(String detailAddress) { this.detailAddress = detailAddress; }
}
