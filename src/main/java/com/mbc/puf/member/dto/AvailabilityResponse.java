package com.mbc.puf.member.dto;

/** 중복확인 결과 (아이디/이메일 공용). available=true 이면 사용 가능 */
public class AvailabilityResponse {

    private final boolean available;

    public AvailabilityResponse(boolean available) {
        this.available = available;
    }

    public boolean isAvailable() {
        return available;
    }
}
