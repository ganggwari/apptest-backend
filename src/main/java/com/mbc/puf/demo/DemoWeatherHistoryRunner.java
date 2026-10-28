package com.mbc.puf.demo;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mbc.puf.common.jpa.DbClock;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 서버를 켤 때 지역별 과거 날씨(weather)를 평상시 비 곡선(DemoCalmRain)으로 채운다.
 * 침수모델은 직전 1·3·6·12·24시간 누적 강수량을 입력으로 쓰는데, DB를 새로 만든 직후에는 이 값이 비어 있어서 점수가 낮게만 나온다.
 * 지역마다 "이미 있는 가장 오래된 행" 앞쪽만 채우므로 켤 때마다 켜 둬도 안전하다 (puf.demo.enabled=true 일 때만 동작).
 * 실패해도 서버 시작은 막지 않고 로그만 남긴다.
 */
@Component
@ConditionalOnProperty(name = "puf.demo.enabled", havingValue = "true")
public class DemoWeatherHistoryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoWeatherHistoryRunner.class);

    @PersistenceContext
    private EntityManager em;

    private final TransactionTemplate tx;
    private final DbClock dbClock;
    private final boolean enabled;
    private final int hours;
    private final int stepMinutes;

    public DemoWeatherHistoryRunner(PlatformTransactionManager txManager, DbClock dbClock,
            @Value("${puf.demo.calm-rain.enabled:true}") boolean calmRain,
            @Value("${puf.demo.calm-rain.history-on-startup:true}") boolean historyOnStartup,
            @Value("${puf.demo.calm-rain.history-hours:24}") int hours,
            @Value("${puf.demo.calm-score-minutes:5}") int calmScoreMinutes) {
        this.tx = new TransactionTemplate(txManager);
        this.dbClock = dbClock;
        this.enabled = calmRain && historyOnStartup;
        this.hours = Math.max(1, Math.min(48, hours));
        this.stepMinutes = Math.max(1, calmScoreMinutes);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        try {
            long t0 = System.currentTimeMillis();
            Integer rows = tx.execute(status -> fill());
            log.info("[demo] 과거 날씨 채우기: {}행 (과거 {}시간, {}분 간격, {}ms)", rows, hours, stepMinutes,
                    System.currentTimeMillis() - t0);
        } catch (RuntimeException e) {
            log.warn("[demo] 과거 날씨 채우기 실패: {}", e.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private int fill() {
        // 다른 인스턴스와 겹치지 않게 같은 트랜잭션 잠금을 건다
        em.createNativeQuery("SELECT TRUE FROM pg_advisory_xact_lock(731905211)").getSingleResult();
        LocalDateTime now = dbClock.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime from = now.minusHours(hours);
        List<Object[]> areas = em.createNativeQuery(
                "SELECT a.id, (SELECT MIN(w.observed_at) FROM weather w WHERE w.area_id = a.id AND w.observed_at >= :from) "
                        + "FROM weather_area a ORDER BY a.id")
                .setParameter("from", from).getResultList();
        int total = 0;
        for (Object[] a : areas) {
            long areaId = ((Number) a[0]).longValue();
            // Hibernate 7 은 timestamp 를 LocalDateTime 으로 돌려준다 (예전 java.sql.Timestamp 도 같이 받는다)
            LocalDateTime firstExisting = a[1] == null ? null
                    : (a[1] instanceof LocalDateTime l ? l : ((Timestamp) a[1]).toLocalDateTime());
            LocalDateTime end = firstExisting == null ? now : firstExisting.minusMinutes(stepMinutes);
            StringBuilder values = new StringBuilder();
            int n = 0;
            for (LocalDateTime t = from; !t.isAfter(end); t = t.plusMinutes(stepMinutes)) {
                double rain = DemoCalmRain.rainPerObservation(areaId, t, stepMinutes);
                double p = Math.min(1, rain * 60.0 / stepMinutes / 20.0);
                double temp = 21 + (areaId % 4) + 2 * Math.sin(t.getHour() / 24.0 * 2 * Math.PI) - 2 * p;
                int humidity = (int) Math.min(95, 55 + (areaId % 10) + 25 * p);
                if (n++ > 0) {
                    values.append(',');
                }
                values.append(String.format(java.util.Locale.ROOT, "(%d,%.1f,%d,%.2f,TIMESTAMP '%s')",
                        areaId, temp, humidity, rain, t.truncatedTo(ChronoUnit.SECONDS).toString().replace('T', ' ')
                                + (t.getSecond() == 0 ? ":00" : "")));
            }
            if (n > 0) {
                total += em.createNativeQuery("INSERT INTO weather (area_id, temp, humidity, rain, observed_at) VALUES "
                        + values + " ON CONFLICT DO NOTHING").executeUpdate();
            }
        }
        return total;
    }
}