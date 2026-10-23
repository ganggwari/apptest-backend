package com.mbc.puf.underpass.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.enums.UnderpassCurrentStatus;

/** 지하차도 목록의 한 항목. 지역명/최신 위험점수(숫자)/판별대기·잔류차량 건수는 대시보드에서
 *  한 번의 목록 호출로 전부 보여주기 위해 추가된 조회 전용 필드 (LEFT/LATERAL JOIN 결과) */
public class UnderpassListItem {

    private Long id;
    private String name;
    private Long regionId;
    private String regionName;      // 대시보드용: region.name
    private String roadAddress;
    private BigDecimal latitude;    // MP-04 전국 마커지도용
    private BigDecimal longitude;   // MP-04 전국 마커지도용
    private UnderpassCurrentStatus currentStatus;   // 조회 전용 표시값
    private RiskLevel currentRiskLevel;             // 조회 전용 표시값 (지도·목록에서 색상 표시용)
    private BigDecimal riskScore;   // 대시보드용: risk_score 최신 1건 (숫자 위험점수)
    private int floodHistoryCount;
    private LocalDateTime createdAt;
    private long pendingReviewCount;  // 대시보드용: 이 지하차도 소속 카메라의 판별대기(PENDING) 건수
    private boolean manageable;   // 로그인한 관리자가 이 시설을 관리(차단·수정 등)할 수 있는지. 담당 지역이면 true, 그 외(비로그인·고객 포함)는 false
    private long remainingCount;      // 대시보드용: 이 지하차도의 잔류차량(REMAINING) 건수

    public UnderpassListItem() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isManageable() { return manageable; }
    public void setManageable(boolean manageable) { this.manageable = manageable; }

    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }

    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }

    public String getRoadAddress() { return roadAddress; }
    public void setRoadAddress(String roadAddress) { this.roadAddress = roadAddress; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public UnderpassCurrentStatus getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(UnderpassCurrentStatus currentStatus) { this.currentStatus = currentStatus; }

    public RiskLevel getCurrentRiskLevel() { return currentRiskLevel; }
    public void setCurrentRiskLevel(RiskLevel currentRiskLevel) { this.currentRiskLevel = currentRiskLevel; }

    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }

    public int getFloodHistoryCount() { return floodHistoryCount; }
    public void setFloodHistoryCount(int floodHistoryCount) { this.floodHistoryCount = floodHistoryCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public long getPendingReviewCount() { return pendingReviewCount; }
    public void setPendingReviewCount(long pendingReviewCount) { this.pendingReviewCount = pendingReviewCount; }

    public long getRemainingCount() { return remainingCount; }
    public void setRemainingCount(long remainingCount) { this.remainingCount = remainingCount; }
}