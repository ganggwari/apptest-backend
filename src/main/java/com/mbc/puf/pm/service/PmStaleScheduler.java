package com.mbc.puf.pm.service;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 일정 간격으로 센서 미수신을 확인한다.
 *  puf.pm.stale.enabled       = true/false (기본 true). 센서 데이터가 계속 들어오지 않는 환경(센서 stub·실제 수신 없음)에서는 끈다
 *  puf.pm.stale.check-seconds = 확인 간격 (기본 30초)
 */
@Component
public class PmStaleScheduler {

    private static final Logger log = LoggerFactory.getLogger(PmStaleScheduler.class);

    private final PmStaleService staleService;
    private final boolean enabled;

    public PmStaleScheduler(PmStaleService staleService, @Value("${puf.pm.stale.enabled:true}") boolean enabled) {
        this.staleService = staleService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${puf.pm.stale.check-seconds:30}", initialDelayString = "${puf.pm.stale.initial-delay-seconds:60}",
            timeUnit = TimeUnit.SECONDS)
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            int created = staleService.raiseNoDataEvents();
            if (created > 0) {
                log.info("[pm] 센서 미수신 이벤트 {}건 생성", created);
            }
        } catch (RuntimeException e) {
            log.warn("[pm] 센서 미수신 확인 실패: {}", e.toString());
        }
    }
}
