package com.mbc.puf.pm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.mbc.puf.pm.dto.PmSensorBackfillResponse;

/**
 * 서버를 켤 때 센서 데이터가 없는 설비에만 과거 데이터를 채운다 (puf.pm.backfill.on-startup=true 일 때).
 * 이미 데이터가 있는 설비는 건너뛰므로 켤 때마다 켜 둬도 안전하다.
 * 시간이 걸리는 작업이라 실패해도 서버 시작은 막지 않고 로그만 남긴다.
 */
@Component
public class PmSensorBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PmSensorBackfillRunner.class);

    private final PmSensorBackfillService backfillService;
    private final boolean onStartup;

    public PmSensorBackfillRunner(PmSensorBackfillService backfillService,
            @Value("${puf.pm.backfill.on-startup:false}") boolean onStartup) {
        this.backfillService = backfillService;
        this.onStartup = onStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!onStartup) {
            return;
        }
        try {
            PmSensorBackfillResponse r = backfillService.backfill(null, null, null, false, null);
            r.byType().forEach(t -> log.info("[pm] 센서 백필 {}: 설비 {}대, {}행, {}ms", t.type(), t.equipments(), t.rows(), t.elapsedMs()));
            log.info("[pm] 센서 백필 끝 (이미 데이터가 있어 건너뛴 설비 {}대)", r.skipped());
        } catch (RuntimeException e) {
            log.warn("[pm] 센서 백필 실패: {}", e.toString());
        }
    }
}
