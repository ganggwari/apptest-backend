package com.mbc.puf.dashboard.dto;

/** 통제 통계(기간 내). blockCount/releaseCount는 기간 내 발생 건수 그대로이고,
 *  duration(분 단위)은 그 기간 안에서 BLOCK 다음 바로 RELEASE로 이어진 쌍만 집계한다
 *  (기간 경계에 걸쳐 끊긴 쌍, 아직 해제 안 된 BLOCK은 duration 계산에서 제외됨).
 *  집계 대상 쌍이 하나도 없으면 두 duration 필드는 null */
public class ControlStatsSummary {

    private long blockCount;
    private long releaseCount;
    private Long totalDurationMinutes;
    private Long avgDurationMinutes;

    public ControlStatsSummary() {
    }

    public long getBlockCount() { return blockCount; }
    public void setBlockCount(long blockCount) { this.blockCount = blockCount; }

    public long getReleaseCount() { return releaseCount; }
    public void setReleaseCount(long releaseCount) { this.releaseCount = releaseCount; }

    public Long getTotalDurationMinutes() { return totalDurationMinutes; }
    public void setTotalDurationMinutes(Long totalDurationMinutes) { this.totalDurationMinutes = totalDurationMinutes; }

    public Long getAvgDurationMinutes() { return avgDurationMinutes; }
    public void setAvgDurationMinutes(Long avgDurationMinutes) { this.avgDurationMinutes = avgDurationMinutes; }
}
