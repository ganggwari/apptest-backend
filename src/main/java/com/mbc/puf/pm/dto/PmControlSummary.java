package com.mbc.puf.pm.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 해당 기간의 차단/해제 전체 건수와 최신 100건. 자동 통제는 operatorName이 null이다. */
public record PmControlSummary(long blockCount, long releaseCount, List<Item> events) {
    public record Item(Long eventId, Long underpassId, String underpassName, String type,
            LocalDateTime createdAt, boolean automatic, String reason, String reasonNote, String operatorName) {}
}
