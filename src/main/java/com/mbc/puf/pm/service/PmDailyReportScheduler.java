package com.mbc.puf.pm.service;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mbc.puf.common.enums.ManagerNotificationCategory;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.notification.service.NotificationService;
import com.mbc.puf.pm.dto.PmDailyReport;
import com.mbc.puf.pm.dto.PmPeriodSummary;
import com.mbc.puf.stream.StreamPublisher;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 매일 정해진 시각에 어제 하루치 예지보전 데일리 리포트를 지역마다 만들어 관리자에게 알린다.
 * 리포트 내용은 따로 저장하지 않고(조회할 때 계산), 알림(EQUIPMENT)에 요약만 남긴다.
 * 알림을 누르면 ref_type=PM_REPORT, ref_id=날짜(yyyyMMdd 숫자)로 해당 날짜 리포트를 열면 된다.
 *
 *  puf.pm.daily-report-enabled = true/false (기본 true)
 *  puf.pm.daily-report-cron    = 실행 시각 (기본 매일 08:00, 서울 시간)
 */
@Component
public class PmDailyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(PmDailyReportScheduler.class);

    @PersistenceContext
    private EntityManager em;

    private final PmReportService reportService;
    private final NotificationService notificationService;
    private final StreamPublisher streamPublisher;
    private final DbClock dbClock;
    private final boolean enabled;

    public PmDailyReportScheduler(PmReportService reportService, NotificationService notificationService,
            StreamPublisher streamPublisher, DbClock dbClock,
            @Value("${puf.pm.daily-report-enabled:true}") boolean enabled) {
        this.reportService = reportService;
        this.notificationService = notificationService;
        this.streamPublisher = streamPublisher;
        this.dbClock = dbClock;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${puf.pm.daily-report-cron:0 0 8 * * *}", zone = "Asia/Seoul")
    public void run() {
        if (!enabled) {
            return;
        }
        publishFor(dbClock.now().toLocalDate().minusDays(1));
    }

    /** 지정한 날짜의 리포트 알림을 지역마다 보낸다 (시험·재발송용으로 따로 열어 둠) */
    public void publishFor(LocalDate date) {
        @SuppressWarnings("unchecked")
        List<Number> regions = em.createNativeQuery("""
                SELECT DISTINCT u.region_id FROM pm_equipment e
                  JOIN underpass u ON u.id = e.underpass_id AND u.is_deleted = FALSE AND e.is_deleted = FALSE
                 ORDER BY 1
                """).getResultList();
        long refId = Long.parseLong(date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        for (Number region : regions) {
            Long regionId = region.longValue();
            try {
                PmDailyReport report = reportService.buildDailyReport(List.of(regionId), null, date);
                PmPeriodSummary s = report.summary();
                String body = String.format("%s · 신규 점검 %d건 · 점검 기록 %d건 · 미처리 %d건 · 위험 %d · 주의 %d",
                        date, s.newEventCount(), s.actionCount(), s.openEventCount(), s.levelCounts().danger(),
                        s.levelCounts().caution());
                notificationService.notifyManagers(ManagerNotificationCategory.EQUIPMENT, regionId,
                        "예지보전 데일리 리포트", body, "PM_REPORT", refId);
                streamPublisher.dataChangedToAdmins(regionId, "pm-report", "CREATE", refId);
            } catch (RuntimeException e) {
                log.warn("[pm] 데일리 리포트 알림 실패 region={} date={}: {}", regionId, date, e.toString());
            }
        }
    }
}
