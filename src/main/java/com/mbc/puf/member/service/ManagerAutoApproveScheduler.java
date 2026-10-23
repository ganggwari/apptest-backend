package com.mbc.puf.member.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.member.repository.ManagerRepository;

/**
 * 관리자 가입 자동 승인. 승인 절차는 흉내만 낸다:
 * 가입한 지 app.manager.auto-approve-after-seconds(기본 60초)가 지난 PENDING 계정을 ACTIVE 로 바꾸고 approved_at 을 채운다.
 * 시각 비교는 DB 시계 기준(created_at 이 DB NOW() 로 들어가므로).
 */
@Component
public class ManagerAutoApproveScheduler {

    private static final Logger log = LoggerFactory.getLogger(ManagerAutoApproveScheduler.class);

    private final ManagerRepository managerRepository;
    private final DbClock dbClock;
    private final int approveAfterSeconds;

    public ManagerAutoApproveScheduler(ManagerRepository managerRepository, DbClock dbClock,
            @Value("${app.manager.auto-approve-after-seconds:60}") int approveAfterSeconds) {
        this.managerRepository = managerRepository;
        this.dbClock = dbClock;
        this.approveAfterSeconds = approveAfterSeconds;
    }

    // 15초마다 확인
    @Scheduled(fixedDelay = 15_000)
    @Transactional
    public void run() {
        LocalDateTime now = dbClock.now();
        int approved = managerRepository.approvePendingCreatedBefore(now.minusSeconds(approveAfterSeconds), now);
        if (approved > 0) {
            log.info("관리자 가입 자동 승인: {}건", approved);
        }
    }
}
