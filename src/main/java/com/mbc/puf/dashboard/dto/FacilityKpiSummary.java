package com.mbc.puf.dashboard.dto;

/** 대시보드 KPI 카드 (facCards/sKpi). regionId 스코프(또는 전체)로 집계한 결과 한 줄.
 *  dangerLevelCount/nearDangerCount는 "지금 위험점수 vs 이 시설의 DANGER 임계치"를 DB에서 직접
 *  비교해서 낸 값 — 프론트에서 위험점수·임계치 두 응답을 따로 받아 계산할 필요 없게 하기 위함 */
public class FacilityKpiSummary {

    private long facilityCount;
    private long blockedCount;
    private long pendingReviewCount;
    private long remainingCount;
    private long alertsToday;
    private long dangerLevelCount;   // 최신 위험점수가 DANGER 임계치 이상인 시설 수
    private long nearDangerCount;    // DANGER 임계치까지 nearMargin 이내로 근접한 시설 수 (초과는 제외, dangerLevelCount와 겹치지 않음)

    public FacilityKpiSummary() {
    }

    public long getFacilityCount() { return facilityCount; }
    public void setFacilityCount(long facilityCount) { this.facilityCount = facilityCount; }

    public long getBlockedCount() { return blockedCount; }
    public void setBlockedCount(long blockedCount) { this.blockedCount = blockedCount; }

    public long getPendingReviewCount() { return pendingReviewCount; }
    public void setPendingReviewCount(long pendingReviewCount) { this.pendingReviewCount = pendingReviewCount; }

    public long getRemainingCount() { return remainingCount; }
    public void setRemainingCount(long remainingCount) { this.remainingCount = remainingCount; }

    public long getAlertsToday() { return alertsToday; }
    public void setAlertsToday(long alertsToday) { this.alertsToday = alertsToday; }

    public long getDangerLevelCount() { return dangerLevelCount; }
    public void setDangerLevelCount(long dangerLevelCount) { this.dangerLevelCount = dangerLevelCount; }

    public long getNearDangerCount() { return nearDangerCount; }
    public void setNearDangerCount(long nearDangerCount) { this.nearDangerCount = nearDangerCount; }
}