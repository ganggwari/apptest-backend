package com.mbc.puf.pm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.pm.dto.PmDailyReport;
import com.mbc.puf.pm.dto.PmPeriodSummary;

/** 예지보전 리포트 집계 (데일리 리포트, 교대 인수인계에 들어가는 예지보전 요약) */
public interface PmReportService {

    /** 하루치 리포트. date 를 안 주면 오늘(지금까지). 미래 날짜는 400 */
    PmDailyReport getDailyReport(LoginUser loginUser, ScopeQuery scope, LocalDate date);

    /** 지역들의 하루치 리포트 (스케줄러·알림용, 권한 확인 없음) */
    PmDailyReport buildDailyReport(List<Long> regionIds, Long underpassId, LocalDate date);

    /** [from, to) 기간 요약 (권한 확인 없음, 호출하는 쪽에서 지역을 정해서 넘긴다) */
    PmPeriodSummary summarize(List<Long> regionIds, Long underpassId, LocalDateTime from, LocalDateTime to);
}
