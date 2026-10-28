package com.mbc.puf.pm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 로컬 시연 전용. 초기 백필이 끝난 뒤부터 새 센서값을 추가한다. */
@Component
@ConditionalOnProperty(name = "puf.pm.sensor-stub.enabled", havingValue = "true")
public class PmSensorStubScheduler {
    private static final Logger log = LoggerFactory.getLogger(PmSensorStubScheduler.class);
    private final PmSensorBackfillService sensors;
    private final int maxSamples;
    private volatile boolean ready;

    public PmSensorStubScheduler(PmSensorBackfillService sensors,
            @Value("${puf.pm.sensor-stub.max-catch-up-samples:12}") int maxSamples) {
        if (maxSamples < 1 || maxSamples > 360) {
            throw new IllegalArgumentException("puf.pm.sensor-stub.max-catch-up-samples must be 1..360");
        }
        this.sensors = sensors;
        this.maxSamples = maxSamples;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        ready = true;
        log.info("[pm-sensor-stub] enabled: equipment sensor_interval_sec, max catch-up {}", maxSamples);
    }

    @Scheduled(initialDelay = 5000, fixedDelayString = "${puf.pm.sensor-stub.tick-ms:5000}")
    public void tick() {
        if (!ready) {
            return;
        }
        try {
            int rows = sensors.appendRecent(maxSamples);
            if (rows > 0) {
                log.debug("[pm-sensor-stub] appended {} rows", rows);
            }
        } catch (RuntimeException e) {
            log.warn("[pm-sensor-stub] append failed; retry next tick", e);
        }
    }
}
