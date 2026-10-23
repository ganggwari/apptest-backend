package com.mbc.puf.plate.dto;

import java.time.LocalDateTime;

import com.mbc.puf.common.enums.ReviewStatus;

/** 수동 판별 처리 결과 */
public class PlateLogReviewResponse {

    private final Long id;
    private final String correctedNum;
    private final Long carId;
    private final ReviewStatus reviewStatus;
    private final Long reviewedBy;
    private final LocalDateTime reviewedAt;

    public PlateLogReviewResponse(Long id, String correctedNum, Long carId, ReviewStatus reviewStatus,
                                  Long reviewedBy, LocalDateTime reviewedAt) {
        this.id = id;
        this.correctedNum = correctedNum;
        this.carId = carId;
        this.reviewStatus = reviewStatus;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
    }

    public Long getId() { return id; }
    public String getCorrectedNum() { return correctedNum; }
    public Long getCarId() { return carId; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public Long getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
}
