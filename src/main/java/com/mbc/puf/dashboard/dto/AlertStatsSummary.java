package com.mbc.puf.dashboard.dto;

/** 알림 발송 통계(기간 내, 유형별 합계) */
public class AlertStatsSummary {

    private long totalCount;
    private long predictiveCount;
    private long roiFinalCount;

    public AlertStatsSummary() {
    }

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }

    public long getPredictiveCount() { return predictiveCount; }
    public void setPredictiveCount(long predictiveCount) { this.predictiveCount = predictiveCount; }

    public long getRoiFinalCount() { return roiFinalCount; }
    public void setRoiFinalCount(long roiFinalCount) { this.roiFinalCount = roiFinalCount; }
}
