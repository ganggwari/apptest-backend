package com.mbc.puf.underpass.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.enums.UnderpassCurrentStatus;

/** 지하차도 상세 응답. currentStatus/currentBlockEventId/currentRiskLevel/currentRiskScore/상하행 입출구 카메라는
 *  조회 전용(이 CRUD로는 변경 불가) */
public class UnderpassDetailResponse {

    private Long id;
    private String name;
    private Long regionId;
    private boolean manageable;   // 로그인한 관리자가 이 시설을 관리할 수 있는지(담당 지역이면 true)
    private String roadAddress;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal heightM;     // 높이(m, 층고) — AI 모델 입력값
    private BigDecimal lengthM;
    private BigDecimal widthM;
    private Integer lanes;
    private String safetyGrade;
    private BigDecimal riverDistM;
    private BigDecimal seaDistM;
    private int floodHistoryCount;
    private boolean earlyBlock;          // 선제 차단 대상(수위 5cm 에서 차단. 침수 이력이 있어도 5cm)
    private BigDecimal currentWaterCm;   // 수위계 최신값(cm)
    private UnderpassCurrentStatus currentStatus;
    private Long currentBlockEventId;
    private RiskLevel currentRiskLevel;
    private BigDecimal currentRiskScore;
    private Long upEntryCameraId;
    private Long upExitCameraId;
    private Long downEntryCameraId;
    private Long downExitCameraId;
    private LocalDateTime createdAt;

    public UnderpassDetailResponse() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isManageable() { return manageable; }
    public void setManageable(boolean manageable) { this.manageable = manageable; }

    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }

    public String getRoadAddress() { return roadAddress; }
    public void setRoadAddress(String roadAddress) { this.roadAddress = roadAddress; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public BigDecimal getHeightM() { return heightM; }
    public void setHeightM(BigDecimal heightM) { this.heightM = heightM; }

    public BigDecimal getLengthM() { return lengthM; }
    public void setLengthM(BigDecimal lengthM) { this.lengthM = lengthM; }

    public boolean isEarlyBlock() { return earlyBlock; }
    public void setEarlyBlock(boolean earlyBlock) { this.earlyBlock = earlyBlock; }

    public BigDecimal getCurrentWaterCm() { return currentWaterCm; }
    public void setCurrentWaterCm(BigDecimal currentWaterCm) { this.currentWaterCm = currentWaterCm; }

    public int getFloodHistoryCount() { return floodHistoryCount; }
    public void setFloodHistoryCount(int floodHistoryCount) { this.floodHistoryCount = floodHistoryCount; }

    public UnderpassCurrentStatus getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(UnderpassCurrentStatus currentStatus) { this.currentStatus = currentStatus; }

    public Long getCurrentBlockEventId() { return currentBlockEventId; }
    public void setCurrentBlockEventId(Long currentBlockEventId) { this.currentBlockEventId = currentBlockEventId; }

    public RiskLevel getCurrentRiskLevel() { return currentRiskLevel; }
    public void setCurrentRiskLevel(RiskLevel currentRiskLevel) { this.currentRiskLevel = currentRiskLevel; }

    public BigDecimal getWidthM() { return widthM; }
    public void setWidthM(BigDecimal widthM) { this.widthM = widthM; }

    public Integer getLanes() { return lanes; }
    public void setLanes(Integer lanes) { this.lanes = lanes; }

    public String getSafetyGrade() { return safetyGrade; }
    public void setSafetyGrade(String safetyGrade) { this.safetyGrade = safetyGrade; }

    public BigDecimal getRiverDistM() { return riverDistM; }
    public void setRiverDistM(BigDecimal riverDistM) { this.riverDistM = riverDistM; }

    public BigDecimal getSeaDistM() { return seaDistM; }
    public void setSeaDistM(BigDecimal seaDistM) { this.seaDistM = seaDistM; }

    public BigDecimal getCurrentRiskScore() { return currentRiskScore; }
    public void setCurrentRiskScore(BigDecimal currentRiskScore) { this.currentRiskScore = currentRiskScore; }

    public Long getUpEntryCameraId() { return upEntryCameraId; }
    public void setUpEntryCameraId(Long upEntryCameraId) { this.upEntryCameraId = upEntryCameraId; }

    public Long getUpExitCameraId() { return upExitCameraId; }
    public void setUpExitCameraId(Long upExitCameraId) { this.upExitCameraId = upExitCameraId; }

    public Long getDownEntryCameraId() { return downEntryCameraId; }
    public void setDownEntryCameraId(Long downEntryCameraId) { this.downEntryCameraId = downEntryCameraId; }

    public Long getDownExitCameraId() { return downExitCameraId; }
    public void setDownExitCameraId(Long downExitCameraId) { this.downExitCameraId = downExitCameraId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}