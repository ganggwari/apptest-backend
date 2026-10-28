package com.mbc.puf.plate.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mbc.puf.common.enums.RecognitionStatus;
import com.mbc.puf.common.enums.ReviewStatus;

/**
 * 번호판 인식 기록 목록의 한 항목 (GET /api/plate-logs).
 * 예전 판별대기 목록(PlateLogPendingItem)의 필드를 그대로 포함하고, 인식/검토 상태와 정정값 등을 더했다.
 * imagePath는 FastAPI가 캡처 이미지를 같이 보냈을 때만 값이 있음(upload-dir 기준 상대경로, 없으면 null)
 */
public class PlateLogListItem {

    private final Long id;
    private final Long cameraId;
    private final Long underpassId;
    private final String underpassName;
    private final String detectedNum;
    private final String correctedNum;
    private final BigDecimal confidence;
    private final String imagePath;
    private final RecognitionStatus recognitionStatus;
    private final Long carId;
    private final ReviewStatus reviewStatus;
    private final LocalDateTime reviewDeadline;
    private final Long reviewedBy;
    private final LocalDateTime reviewedAt;
    private final LocalDateTime detectedAt;
    // 판별대기 타이머용: 응답 시점 기준 마감까지 남은 초 (대기 상태가 아니거나 마감이 지났으면 0)
    private final long remainingSeconds;
    private String reviewerName;   // 검토(정정)한 직원 이름 (아직 검토 전이면 null)
    private String customerName;   // 매칭된 차량의 고객 이름 (매칭 전이면 null)

    public PlateLogListItem(Long id, Long cameraId, Long underpassId, String underpassName, String detectedNum,
                            String correctedNum, BigDecimal confidence, String imagePath,
                            RecognitionStatus recognitionStatus, Long carId, ReviewStatus reviewStatus,
                            LocalDateTime reviewDeadline, Long reviewedBy, LocalDateTime reviewedAt,
                            LocalDateTime detectedAt, long remainingSeconds) {
        this.id = id;
        this.cameraId = cameraId;
        this.underpassId = underpassId;
        this.underpassName = underpassName;
        this.detectedNum = detectedNum;
        this.correctedNum = correctedNum;
        this.confidence = confidence;
        this.imagePath = imagePath;
        this.recognitionStatus = recognitionStatus;
        this.carId = carId;
        this.reviewStatus = reviewStatus;
        this.reviewDeadline = reviewDeadline;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.detectedAt = detectedAt;
        this.remainingSeconds = remainingSeconds;
    }

    public long getRemainingSeconds() { return remainingSeconds; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public Long getId() { return id; }
    public Long getCameraId() { return cameraId; }
    public Long getUnderpassId() { return underpassId; }
    public String getUnderpassName() { return underpassName; }
    public String getDetectedNum() { return detectedNum; }
    public String getCorrectedNum() { return correctedNum; }
    public BigDecimal getConfidence() { return confidence; }
    public String getImagePath() { return imagePath; }
    public RecognitionStatus getRecognitionStatus() { return recognitionStatus; }
    public Long getCarId() { return carId; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public LocalDateTime getReviewDeadline() { return reviewDeadline; }
    public Long getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getDetectedAt() { return detectedAt; }
}