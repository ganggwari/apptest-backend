package com.mbc.puf.internal.dto;

import java.time.LocalDateTime;

import com.mbc.puf.common.enums.RecognitionStatus;
import com.mbc.puf.common.enums.ReviewStatus;

/** 번호판 인식 결과 수신 응답. recognitionStatus/carId/reviewStatus는 Spring이 계산한 값.
 *  imagePath는 이미지를 같이 받아 저장했을 때만 값이 있음(FastAPI가 저장 성공 여부를 확인하는 용도) */
public class PlateLogResponse {

    private final Long id;
    private final Long cameraId;
    private final RecognitionStatus recognitionStatus;
    private final Long carId;
    private final ReviewStatus reviewStatus;
    private final String imagePath;
    private final LocalDateTime detectedAt;

    public PlateLogResponse(Long id, Long cameraId, RecognitionStatus recognitionStatus, Long carId,
                            ReviewStatus reviewStatus, String imagePath, LocalDateTime detectedAt) {
        this.id = id;
        this.cameraId = cameraId;
        this.recognitionStatus = recognitionStatus;
        this.carId = carId;
        this.reviewStatus = reviewStatus;
        this.imagePath = imagePath;
        this.detectedAt = detectedAt;
    }

    public Long getId() { return id; }
    public Long getCameraId() { return cameraId; }
    public RecognitionStatus getRecognitionStatus() { return recognitionStatus; }
    public Long getCarId() { return carId; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public String getImagePath() { return imagePath; }
    public LocalDateTime getDetectedAt() { return detectedAt; }
}