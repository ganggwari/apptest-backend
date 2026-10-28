package com.mbc.puf.pm.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 분석 건수는 결과 수, 신규/미처리는 이벤트 수. 목록은 최신 100건이며 건수는 전체다.
 * ROAD는 센서 점수 통계에서 제외한다. 판정/이벤트 상태는 조회 시점 현재값이다. */
public record PmSinkholeSummary(long analysisCount, long detectedCount, long newEventCount,
        long openEventCount, List<Item> results, List<Item> openIncidents) {
    public record Item(Long resultId, Long equipId, String name, Long underpassId, String underpassName,
            Long eventId, LocalDateTime analyzedAt, boolean detected, BigDecimal confidence,
            int detectionCount, String reviewStatus, String eventStatus, String imageUrl,
            LocalDateTime reviewedAt, String reviewerName) {}
}
