package com.mbc.puf.display.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 전광판 페이지를 주기적으로 다시 맞춘다.
 *  - 해제 안내(기본 10분)가 시간이 지나 사라져야 할 때: 이벤트가 없으니 시간으로 확인해야 한다
 *  - 서버를 껐다 켠 사이에 바뀐 상태 반영 (시작할 때 한 번)
 * 전광판마다 따로 트랜잭션을 열어서, 하나가 실패해도 나머지는 계속 처리한다.
 */
@Component
public class VmsDisplayScheduler {

    private static final Logger log = LoggerFactory.getLogger(VmsDisplayScheduler.class);

    private final VmsDisplayService vmsDisplayService;

    public VmsDisplayScheduler(VmsDisplayService vmsDisplayService) {
        this.vmsDisplayService = vmsDisplayService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        sync();
    }

    @Scheduled(initialDelay = 60_000, fixedDelayString = "#{${puf.display.sync-seconds:60} * 1000}")
    public void sync() {
        try {
            int changed = 0;
            for (Long vmsId : vmsDisplayService.targetVmsIds()) {
                try {
                    if (vmsDisplayService.reconcileVms(vmsId, null, null)) {
                        changed++;
                    }
                } catch (RuntimeException e) {
                    log.warn("[vms] 전광판 {} 동기화 실패: {}", vmsId, e.toString());
                }
            }
            if (changed > 0) {
                log.info("[vms] 전광판 {}대 페이지 갱신", changed);
            }
        } catch (RuntimeException e) {
            log.warn("[vms] 전광판 동기화 실패: {}", e.toString());
        }
    }
}
