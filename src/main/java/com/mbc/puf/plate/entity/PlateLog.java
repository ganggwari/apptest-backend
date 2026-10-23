package com.mbc.puf.plate.entity;

import com.mbc.puf.common.enums.RecognitionStatus;
import com.mbc.puf.common.enums.ReviewStatus;
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

/** 번호판 인식 이벤트 (테이블: plate_log) */
@Entity
@Table(name = "plate_log")
public class PlateLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "camera_id")
    private Long cameraId;

    @Column(name = "detected_num")
    private String detectedNum;

    @Column(name = "corrected_num")
    private String correctedNum;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Column(name = "image")
    private String imagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "recog_status")
    private RecognitionStatus recognitionStatus;

    @Column(name = "car_id")
    private Long carId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status")
    private ReviewStatus reviewStatus = ReviewStatus.NONE;

    @Column(name = "review_due")
    private LocalDateTime reviewDeadline;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    public PlateLog() {
    }

    public Long getId() {
        return id;
    }

    public Long getCameraId() {
        return cameraId;
    }

    public void setCameraId(Long cameraId) {
        this.cameraId = cameraId;
    }

    public String getDetectedNum() {
        return detectedNum;
    }

    public void setDetectedNum(String detectedNum) {
        this.detectedNum = detectedNum;
    }

    public String getCorrectedNum() {
        return correctedNum;
    }

    public void setCorrectedNum(String correctedNum) {
        this.correctedNum = correctedNum;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public RecognitionStatus getRecognitionStatus() {
        return recognitionStatus;
    }

    public void setRecognitionStatus(RecognitionStatus recognitionStatus) {
        this.recognitionStatus = recognitionStatus;
    }

    public Long getCarId() {
        return carId;
    }

    public void setCarId(Long carId) {
        this.carId = carId;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public LocalDateTime getReviewDeadline() {
        return reviewDeadline;
    }

    public void setReviewDeadline(LocalDateTime reviewDeadline) {
        this.reviewDeadline = reviewDeadline;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(LocalDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }
}
