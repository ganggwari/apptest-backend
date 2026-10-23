package com.mbc.puf.underpass.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mbc.puf.common.enums.CameraRole;

/** 카메라 응답 (목록/상세 공용) */
public class CameraResponse {

    private final Long id;
    private final Long underpassId;
    private final CameraRole cameraRole;
    private final String streamUrl;
    private final Integer distanceM;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final LocalDateTime createdAt;

    public CameraResponse(Long id, Long underpassId, CameraRole cameraRole, String streamUrl, Integer distanceM,
                          BigDecimal latitude, BigDecimal longitude, LocalDateTime createdAt) {
        this.id = id;
        this.underpassId = underpassId;
        this.cameraRole = cameraRole;
        this.streamUrl = streamUrl;
        this.distanceM = distanceM;
        this.latitude = latitude;
        this.longitude = longitude;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUnderpassId() { return underpassId; }
    public CameraRole getCameraRole() { return cameraRole; }
    public String getStreamUrl() { return streamUrl; }
    public Integer getDistanceM() { return distanceM; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
