package com.mbc.puf.pm.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 예지보전 데일리 리포트 (GET /api/pm/reports/daily). date 하루치(오늘이면 지금까지) 요약 */
public record PmDailyReport(LocalDate date, LocalDateTime generatedAt, PmPeriodSummary summary) {
}
