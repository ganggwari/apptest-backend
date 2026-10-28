package com.mbc.puf.internal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 서버를 켤 때 지하차도 위험점수의 과거 이력을 시연용으로 채운다 (puf.risk.history-backfill.on-startup=true 일 때).
 * 이미 이력이 충분한 지하차도는 건너뛰므로 켤 때마다 켜 둬도 안전하다.
 * 실패해도 서버 시작은 막지 않고 로그만 남긴다.
 */
@Component
public class RiskHistoryBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RiskHistoryBackfillRunner.class);

    private final RiskHistoryBackfillService backfillService;
    private final boolean onStartup;
    private final int hours;
    private final int stepMinutes;
    private final double calmBase;

    public RiskHistoryBackfillRunner(RiskHistoryBackfillService backfillService,
            @Value("${puf.risk.history-backfill.on-startup:false}") boolean onStartup,
            @Value("${puf.risk.history-backfill.hours:6}") int hours,
            @Value("${puf.risk.history-backfill.step-minutes:10}") int stepMinutes,
            @Value("${puf.risk.history-backfill.calm-score:10}") double calmBase) {
        this.backfillService = backfillService;
        this.onStartup = onStartup;
        this.hours = hours;
        this.stepMinutes = stepMinutes;
        this.calmBase = calmBase;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!onStartup) {
            return;
        }
        try {
            long t0 = System.currentTimeMillis();
            int rows = backfillService.backfill(hours, stepMinutes, calmBase);
            log.info("[risk] 위험점수 과거 이력 백필: {}행 (과거 {}시간, {}분 간격, {}ms)", rows, hours, stepMinutes,
                    System.currentTimeMillis() - t0);
        } catch (RuntimeException e) {
            log.warn("[risk] 위험점수 과거 이력 백필 실패: {}", e.toString());
        }
    }
}
