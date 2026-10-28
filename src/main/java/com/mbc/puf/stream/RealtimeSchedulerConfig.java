package com.mbc.puf.stream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class RealtimeSchedulerConfig {
    /** 기존 스케줄 작업은 기본 스케줄러에 유지한다. */
    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduled-");
        return scheduler;
    }

    /** 외부 AI 서버 응답 지연이 기존 센서/차단/알림 스케줄러를 막지 않도록 분리한다. */
    @Bean("realtimeScheduler")
    public ThreadPoolTaskScheduler realtimeScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("realtime-status-");
        return scheduler;
    }
}
