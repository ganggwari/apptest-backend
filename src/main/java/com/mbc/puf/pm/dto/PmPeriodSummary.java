package com.mbc.puf.pm.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 예지보전 기간 요약. 데일리 리포트와 교대 인수인계가 같이 쓴다.
 *  - levelCounts          : 기간 끝(to) 시점의 단계별 설비 수, previousLevelCounts : 기간 시작(from) 시점 (전일/전 교대 대비용)
 *  - newEvents            : 기간 중 새로 생긴 점검 대기,  actions : 기간 중 남긴 점검 기록
 *  - openEvents           : 지금 아직 처리 중인 점검 대기 (끝날 때까지 다음 리포트에도 계속 나온다 = 이월)
 *  - stale                : 전에는 받았는데 기준 시각 직전 일정 시간 동안 점수가 안 들어온 설비 (데이터 미수신)
 *  - neverReceived        : 점수를 한 번도 못 받은 설비 수 (목록 없이 수만)
 *  - recommended          : 점검 권장 — 기준 시점 주의 이상인 설비를 점수 높은 순으로 최대 10개
 */
public record PmPeriodSummary(
        LocalDateTime from,
        LocalDateTime to,
        PmLevelCounts levelCounts,
        PmLevelCounts previousLevelCounts,
        List<PmTypeCounts> byType,
        int newEventCount,
        List<PmEventItem> newEvents,
        int actionCount,
        List<PmActionItem> actions,
        int openEventCount,
        List<PmEventItem> openEvents,
        List<PmReportEquipment> stale,
        int neverReceived,
        List<PmReportEquipment> recommended) {
}
