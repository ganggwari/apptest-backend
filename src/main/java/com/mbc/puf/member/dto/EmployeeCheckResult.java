package com.mbc.puf.member.dto;

/** 직원 대조 결과 (서비스 → 컨트롤러 전달용 내부 객체). employeeId 는 세션에만 보관하고 클라이언트에는 내보내지 않는다. */
public record EmployeeCheckResult(Long employeeId, String maskedPhone, PhoneCodeTiming timing) {
}