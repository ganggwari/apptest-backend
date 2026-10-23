package com.mbc.puf.dashboard.dto;

/** MP-06 전국 통제현황 그래프. 전체 지하차도를 current_status 기준으로 집계한 결과 */
public class StatusSummaryResponse {

    private long normalCount;
    private long blockedCount;
    private long totalCount;

    public StatusSummaryResponse() {
    }

    public long getNormalCount() { return normalCount; }
    public void setNormalCount(long normalCount) { this.normalCount = normalCount; }

    public long getBlockedCount() { return blockedCount; }
    public void setBlockedCount(long blockedCount) { this.blockedCount = blockedCount; }

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }
}
