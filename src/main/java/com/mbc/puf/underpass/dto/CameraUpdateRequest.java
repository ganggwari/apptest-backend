package com.mbc.puf.underpass.dto;

import java.math.BigDecimal;

import com.mbc.puf.common.enums.CameraRole;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 카메라 수정 요청 */
public class CameraUpdateRequest {

    @NotNull(message = "카메라 역할을 선택해 주세요.")
    private CameraRole cameraRole;

    @Size(max = 255, message = "스트림 주소는 255자 이하여야 합니다.")
    private String streamUrl;

    private Integer distanceM;
    private BigDecimal latitude;
    private BigDecimal longitude;

    public CameraUpdateRequest() {
    }

    public CameraRole getCameraRole() { return cameraRole; }
    public void setCameraRole(CameraRole cameraRole) { this.cameraRole = cameraRole; }

    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }

    public Integer getDistanceM() { return distanceM; }
    public void setDistanceM(Integer distanceM) { this.distanceM = distanceM; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
}
