package com.mbc.puf.member.dto;

/** 관리자 가입 1단계 응답: 인증코드를 보낸 번호(가운데 마스킹, 예: 010-****-5678)와 타이머용 남은 초 */
public record ManagerEmployeeCheckResponse(String maskedPhone, int expiresInSeconds, int resendAfterSeconds) {
}