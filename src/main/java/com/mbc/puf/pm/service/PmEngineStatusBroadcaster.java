package com.mbc.puf.pm.service;

import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.stream.StreamPublisher;
import com.mbc.puf.stream.RealtimePayloads.EngineStatus;

@Component
public class PmEngineStatusBroadcaster {
    private final PmEngineMonitor monitor;
    private final DbClock clock;
    private final StreamPublisher stream;
    private final int delayed;
    private final int down;
    private String previous;
    public PmEngineStatusBroadcaster(PmEngineMonitor monitor, DbClock clock, StreamPublisher stream,
            @Value("${puf.pm.engine.delayed-seconds:180}") int delayed,
            @Value("${puf.pm.engine.down-seconds:600}") int down) {
        this.monitor=monitor; this.clock=clock; this.stream=stream;
        this.delayed=Math.max(1, delayed); this.down=Math.max(this.delayed, down);
    }
    @Scheduled(initialDelay=5000, fixedDelay=15000, scheduler="realtimeScheduler")
    public synchronized void poll() {
        LocalDateTime at=monitor.getLastReceivedAt();
        Long age=at==null?null:Math.max(0, Duration.between(at,clock.now()).toSeconds());
        String status=age==null?"WAITING":age<=delayed?"NORMAL":age<=down?"DELAYED":"DOWN";
        if (!status.equals(previous)) {
            stream.publishToAdmins(null,"pm-engine-status",new EngineStatus(status,at,age));
            previous=status;
        }
    }
}
