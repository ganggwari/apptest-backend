package com.mbc.puf.internal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** FastAPI가 보내는 번호판 인식 결과 (POST /api/internal/plate-logs).
 *  detectedNum/confidence는 못 읽었으면 null로 보낼 수 있다 */
public class PlateLogCreateRequest {

    @NotNull(message = "cameraId는 필수입니다.")
    private Long cameraId;

    @Size(max = 20, message = "detectedNum은 20자 이하여야 합니다.")   // plate_log.detected_num VARCHAR(20)
    private String detectedNum;

    @DecimalMin(value = "0.000", message = "confidence는 0 이상이어야 합니다.")
    @DecimalMax(value = "1.000", message = "confidence는 1 이하여야 합니다.")   // plate_log.confidence DECIMAL(4,3)
    private BigDecimal confidence;

    @NotNull(message = "detectedAt은 필수입니다.")
    private LocalDateTime detectedAt;

    public PlateLogCreateRequest() {
    }

    public Long getCameraId() { return cameraId; }
    public void setCameraId(Long cameraId) { this.cameraId = cameraId; }

    public String getDetectedNum() { return detectedNum; }
    public void setDetectedNum(String detectedNum) { this.detectedNum = detectedNum; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
}
