package com.mbc.puf.underpass.dto;

import java.math.BigDecimal;

import com.mbc.puf.common.enums.CameraRole;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 카메라 등록 요청. underpassId는 URL 경로에서 받으므로 여기 없다 */
public class CameraCreateRequest {

    @NotNull(message = "카메라 역할을 선택해 주세요.")
    private CameraRole cameraRole;

    @Size(max = 255, message = "스트림 주소는 255자 이하여야 합니다.")
    private String streamUrl;

    private Integer distanceM;
    private BigDecimal latitude;
    private BigDecimal longitude;

    public CameraCreateRequest() {
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
