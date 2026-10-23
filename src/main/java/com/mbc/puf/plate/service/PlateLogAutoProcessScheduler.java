package com.mbc.puf.plate.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** review_deadline이 지난 PENDING 건을 주기적으로 AUTO_PROCESSED로 정리. FinalApplication에 @EnableScheduling 필요 */
@Component
public class PlateLogAutoProcessScheduler {

    private final PlateLogReviewService plateLogReviewService;

    public PlateLogAutoProcessScheduler(PlateLogReviewService plateLogReviewService) {
        this.plateLogReviewService = plateLogReviewService;
    }

    // 30초마다 확인 (review_deadline 정밀도가 1분이라 이 정도면 충분)
    @Scheduled(fixedRate = 30_000)
    public void run() {
        plateLogReviewService.autoProcessExpired();
    }
}
