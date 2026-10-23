package com.mbc.puf.member.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

/** 고객 마이페이지 내 정보 조회 결과 */
public class CustomerProfileResponse {

    private final Long id;
    private final String loginId;
    private final String name;
    private final LocalDate birthDate;
    private final String email;
    private final String phone;
    private final String emergency;
    private final String zipcode;
    private final String roadAddress;
    private final String detailAddress;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private final LocalDateTime createdAt;

    public CustomerProfileResponse(Long id, String loginId, String name, LocalDate birthDate, String email,
                                   String phone, String emergency, String zipcode, String roadAddress,
                                   String detailAddress, LocalDateTime createdAt) {
        this.id = id;
        this.loginId = loginId;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
        this.phone = phone;
        this.emergency = emergency;
        this.zipcode = zipcode;
        this.roadAddress = roadAddress;
        this.detailAddress = detailAddress;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getName() { return name; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getEmergency() { return emergency; }
    public String getZipcode() { return zipcode; }
    public String getRoadAddress() { return roadAddress; }
    public String getDetailAddress() { return detailAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}