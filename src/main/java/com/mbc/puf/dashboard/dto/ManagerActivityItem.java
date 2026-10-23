package com.mbc.puf.dashboard.dto;

/** 관리자별 활동 건수(기간 내: 차단·해제·전광판 조작 합계). 기간 내 활동이 전혀 없는 관리자는
 *  목록에서 빠진다(활동한 사람만 보여주는 용도) */
public class ManagerActivityItem {

    private Long managerId;
    private String managerName;
    private long blockCount;
    private long releaseCount;
    private long displayLogCount;
    private long totalCount;

    public ManagerActivityItem() {
    }

    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }

    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }

    public long getBlockCount() { return blockCount; }
    public void setBlockCount(long blockCount) { this.blockCount = blockCount; }

    public long getReleaseCount() { return releaseCount; }
    public void setReleaseCount(long releaseCount) { this.releaseCount = releaseCount; }

    public long getDisplayLogCount() { return displayLogCount; }
    public void setDisplayLogCount(long displayLogCount) { this.displayLogCount = displayLogCount; }

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }
}
