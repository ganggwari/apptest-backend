package com.mbc.puf.underpass.entity;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.enums.UnderpassCurrentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;

/** 지하차도 (테이블: underpass) */
@Entity
@Table(name = "underpass")
public class Underpass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "road_addr")
    private String roadAddress;

    @Column(name = "lat")
    private BigDecimal latitude;

    @Column(name = "lng")
    private BigDecimal longitude;

    @Column(name = "height")
    private BigDecimal heightM;

    @Column(name = "length")
    private BigDecimal lengthM;

    @Column(name = "width")
    private BigDecimal widthM;

    @Column(name = "lanes")
    private Integer lanes;             // 차로수

    @Column(name = "safety_grade")
    private String safetyGrade;        // 안전등급(A~E), 미실시/불명은 UNKNOWN

    @Column(name = "river_dist")
    private BigDecimal riverDistM;     // 가까운 하천까지 직선거리(m)

    @Column(name = "sea_dist")
    private BigDecimal seaDistM;       // 바다(해안선)까지 직선거리(m)

    @Column(name = "flood_count")
    private int floodHistoryCount = 0;

    // 선제 차단 대상(관리자 지정). 침수 이력이 있거나 true 면 수위 5cm 에서 차단, 아니면 15cm
    @Column(name = "early_block")
    private boolean earlyBlock = false;

    // 상행/하행 입·출구 카메라. 입출구 매칭(transit)과 방향(UP/DOWN) 판정에 쓴다
    @Column(name = "up_entry_cam_id")
    private Long upEntryCameraId;

    @Column(name = "up_exit_cam_id")
    private Long upExitCameraId;

    @Column(name = "down_entry_cam_id")
    private Long downEntryCameraId;

    @Column(name = "down_exit_cam_id")
    private Long downExitCameraId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private UnderpassCurrentStatus currentStatus = UnderpassCurrentStatus.NORMAL;

    @Column(name = "block_event_id")
    private Long currentBlockEventId;

    @Column(name = "risk_level")
    private RiskLevel currentRiskLevel = RiskLevel.SAFE;

    @Column(name = "risk_score")
    private BigDecimal currentRiskScore = BigDecimal.ZERO;   // 최신 위험점수 캐시(FastAPI 수신 때마다 갱신)

    @Column(name = "water_cm")
    private BigDecimal currentWaterCm = BigDecimal.ZERO;   // 수위계 최신값 캐시(cm)

    @Column(name = "is_deleted")
    private boolean deleted = false;

    @Generated   // DB 기본값(NOW())으로 INSERT 시 채워지고, 저장 직후 값을 다시 읽어온다
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Underpass() {
    }

    public boolean isEarlyBlock() {
        return earlyBlock;
    }

    public void setEarlyBlock(boolean earlyBlock) {
        this.earlyBlock = earlyBlock;
    }

    public BigDecimal getCurrentWaterCm() {
        return currentWaterCm;
    }

    public void setCurrentWaterCm(BigDecimal currentWaterCm) {
        this.currentWaterCm = currentWaterCm;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getRegionId() {
        return regionId;
    }

    public void setRegionId(Long regionId) {
        this.regionId = regionId;
    }

    public String getRoadAddress() {
        return roadAddress;
    }

    public void setRoadAddress(String roadAddress) {
        this.roadAddress = roadAddress;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getHeightM() {
        return heightM;
    }

    public void setHeightM(BigDecimal heightM) {
        this.heightM = heightM;
    }

    public BigDecimal getLengthM() {
        return lengthM;
    }

    public void setLengthM(BigDecimal lengthM) {
        this.lengthM = lengthM;
    }

    public int getFloodHistoryCount() {
        return floodHistoryCount;
    }

    public void setFloodHistoryCount(int floodHistoryCount) {
        this.floodHistoryCount = floodHistoryCount;
    }

    public BigDecimal getWidthM() {
        return widthM;
    }

    public void setWidthM(BigDecimal widthM) {
        this.widthM = widthM;
    }

    public Integer getLanes() {
        return lanes;
    }

    public void setLanes(Integer lanes) {
        this.lanes = lanes;
    }

    public String getSafetyGrade() {
        return safetyGrade;
    }

    public void setSafetyGrade(String safetyGrade) {
        this.safetyGrade = safetyGrade;
    }

    public BigDecimal getRiverDistM() {
        return riverDistM;
    }

    public void setRiverDistM(BigDecimal riverDistM) {
        this.riverDistM = riverDistM;
    }

    public BigDecimal getSeaDistM() {
        return seaDistM;
    }

    public void setSeaDistM(BigDecimal seaDistM) {
        this.seaDistM = seaDistM;
    }

    public Long getUpEntryCameraId() {
        return upEntryCameraId;
    }

    public void setUpEntryCameraId(Long upEntryCameraId) {
        this.upEntryCameraId = upEntryCameraId;
    }

    public Long getUpExitCameraId() {
        return upExitCameraId;
    }

    public void setUpExitCameraId(Long upExitCameraId) {
        this.upExitCameraId = upExitCameraId;
    }

    public Long getDownEntryCameraId() {
        return downEntryCameraId;
    }

    public void setDownEntryCameraId(Long downEntryCameraId) {
        this.downEntryCameraId = downEntryCameraId;
    }

    public Long getDownExitCameraId() {
        return downExitCameraId;
    }

    public void setDownExitCameraId(Long downExitCameraId) {
        this.downExitCameraId = downExitCameraId;
    }

    public BigDecimal getCurrentRiskScore() {
        return currentRiskScore;
    }

    public void setCurrentRiskScore(BigDecimal currentRiskScore) {
        this.currentRiskScore = currentRiskScore;
    }

    public UnderpassCurrentStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(UnderpassCurrentStatus currentStatus) {
        this.currentStatus = currentStatus;
    }

    public Long getCurrentBlockEventId() {
        return currentBlockEventId;
    }

    public void setCurrentBlockEventId(Long currentBlockEventId) {
        this.currentBlockEventId = currentBlockEventId;
    }

    public RiskLevel getCurrentRiskLevel() {
        return currentRiskLevel;
    }

    public void setCurrentRiskLevel(RiskLevel currentRiskLevel) {
        this.currentRiskLevel = currentRiskLevel;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}