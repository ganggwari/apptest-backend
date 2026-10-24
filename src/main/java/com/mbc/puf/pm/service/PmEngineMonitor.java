package com.mbc.puf.pm.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

/**
 * 예지보전 서버(FastAPI)에서 마지막으로 점수를 받은 때를 메모리에 기억한다 (엔진 상태 API 용).
 * 서버를 다시 켜면 비워지지만, 그때는 예지보전 서버가 다시 점수를 보내므로 문제가 없다.
 */
@Component
public class PmEngineMonitor {

    private volatile LocalDateTime lastReceivedAt;     // DB 시계 기준, 점수 묶음을 받은 시각
    private volatile int lastBatchSize;
    private volatile boolean lastBatchBackfill;

    public void received(LocalDateTime at, int batchSize, boolean backfill) {
        this.lastReceivedAt = at;
        this.lastBatchSize = batchSize;
        this.lastBatchBackfill = backfill;
    }

    public LocalDateTime getLastReceivedAt() {
        return lastReceivedAt;
    }

    public int getLastBatchSize() {
        return lastBatchSize;
    }

    public boolean isLastBatchBackfill() {
        return lastBatchBackfill;
    }
}
