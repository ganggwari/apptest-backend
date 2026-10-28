package com.mbc.puf.plate.service;

import com.mbc.puf.common.enums.RecognitionStatus;
import com.mbc.puf.common.enums.ReviewStatus;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.request.VehicleQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.plate.dto.PlateLogListItem;
import com.mbc.puf.plate.dto.PlateLogReviewRequest;
import com.mbc.puf.plate.dto.PlateLogReviewResponse;

/** 번호판 인식 기록 조회 + LOW_CONFIDENCE로 PENDING 걸린 건의 수동 판별 */
public interface PlateLogReviewService {

    // 번호판 인식 기록 목록. 판별대기 목록 = reviewStatus=PENDING (마감 임박한 것부터)
    PageResponse<PlateLogListItem> getPlateLogs(LoginUser loginUser, ScopeQuery scope, ReviewStatus reviewStatus,
                                                RecognitionStatus recognitionStatus, VehicleQuery vehicle,
                                                String reviewerName, String customerName,
                                                PeriodQuery period, PageQuery page);

    // 수동 판별: correctedNum으로 재매칭 후 CONFIRMED로 확정
    PlateLogReviewResponse review(LoginUser loginUser, Long id, PlateLogReviewRequest request);

    // 마감 지난 PENDING 건을 AUTO_PROCESSED로 일괄 처리 (스케줄러가 호출)
    void autoProcessExpired();
}
