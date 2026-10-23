package com.mbc.puf.common.request;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;

import com.mbc.puf.common.exception.BusinessException;

/**
 * [공통 조건 묶음 2: Period] 이력성 목록 공통.  ?startDate=2026-10-01&endDate=2026-10-16
 * 둘 다 선택이고, endDate 당일도 포함한다 (startDate 00:00 이상 ~ endDate 다음날 00:00 미만).
 */
public class PeriodQuery {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    public PeriodQuery() {
    }

    public static PeriodQuery of(LocalDate startDate, LocalDate endDate) {
        PeriodQuery q = new PeriodQuery();
        q.startDate = startDate;
        q.endDate = endDate;
        q.validate();
        return q;
    }

    // 시작일이 종료일보다 늦으면 400
    public void validate() {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "startDate는 endDate보다 늦을 수 없습니다.");
        }
    }

    public LocalDateTime startAt() {
        return startDate == null ? null : startDate.atStartOfDay();
    }

    public LocalDateTime endAtExclusive() {
        return endDate == null ? null : endDate.plusDays(1).atStartOfDay();
    }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
