package com.mbc.puf.demo;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.stream.StreamPublisher;

/** 호우 시나리오 시작/중지/상태 조회 (시연 전용) */
@Service
@ConditionalOnProperty(name = "puf.demo.enabled", havingValue = "true")
public class DemoScenarioService {

    private static final Logger log = LoggerFactory.getLogger(DemoScenarioService.class);
    private static final int REFILL_STEP_MIN = 5;

    private final DemoScenarioState state;
    private final JdbcTemplate jdbc;
    private final StreamPublisher streamPublisher;
    private final TransactionTemplate tx;

    public DemoScenarioService(DemoScenarioState state, JdbcTemplate jdbc, StreamPublisher streamPublisher,
            PlatformTransactionManager txManager) {
        this.state = state;
        this.jdbc = jdbc;
        this.streamPublisher = streamPublisher;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 삭제되지 않은 모든 지하차도 id */
    public List<Long> allUnderpassIds() {
        return jdbc.queryForList("SELECT id FROM underpass WHERE is_deleted = FALSE ORDER BY id", Long.class);
    }

    /** 시나리오 시작. 선택한 지역의 지하차도와 그 지하차도들이 쓰는 날씨 지역에만 센 비를 적용한다 */
    public DemoScenarioDtos.Status start(List<Long> regionIds, Double speedParam) {
        if (state.current() != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 진행 중인 시나리오가 있습니다. 먼저 중지하세요.");
        }
        Set<Long> regions = new LinkedHashSet<>(regionIds);
        List<Long> validRegions = jdbc.queryForList(
                "SELECT id FROM region WHERE id IN (" + csv(regions) + ") AND name <> '미배정'", Long.class);
        if (validRegions.size() != regions.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "존재하지 않거나 선택할 수 없는 지역이 있습니다.");
        }
        List<Long> underpasses = jdbc.queryForList(
                "SELECT id FROM underpass WHERE is_deleted = FALSE AND region_id IN (" + csv(regions) + ")", Long.class);
        if (underpasses.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "선택한 지역에 지하차도가 없습니다.");
        }
        // 지하차도 위치에서 가장 가까운 날씨 지역 = 침수모델이 강수를 읽어 가는 지역
        List<Long> areas = jdbc.queryForList("""
                SELECT DISTINCT n.id FROM underpass u
                CROSS JOIN LATERAL (
                    SELECT wa.id FROM weather_area wa
                    ORDER BY power(wa.lat - u.lat, 2) + power(wa.lng - u.lng, 2) LIMIT 1
                ) n
                WHERE u.is_deleted = FALSE AND u.region_id IN (""" + csv(regions) + ")", Long.class);

        double speed = speedParam == null ? 1.0 : speedParam;
        state.begin(new DemoScenarioState.Run(regions, new LinkedHashSet<>(underpasses), new LinkedHashSet<>(areas),
                System.currentTimeMillis(), speed));
        streamPublisher.dataChangedPublic(null, "demo-scenario", "UPDATE", null);
        return status();
    }

    /** 중지: 즉시 평소 상태로 되돌린다 (다음 틱에 전체 값이 평소로 갱신됨) */
    public DemoScenarioDtos.Status stop() {
        finishNow();
        return status();
    }

    public void finishNow() {
        DemoScenarioState.Run r = state.current();
        if (r == null) {
            return;
        }
        state.end();
        refillWithCalmRain(r);
        streamPublisher.dataChangedPublic(null, "demo-scenario", "UPDATE", null);
    }

    /**
     * 시나리오 동안 쌓인 센 비 관측값을 지우고 같은 기간을 평상시 비(DemoCalmRain)로 다시 채운다.
     * 침수모델이 직전 1~24시간 누적 강수량으로 점수를 내므로, 이걸 안 하면 비가 그친 뒤에도 점수·수위가 오래 남는다.
     * 호우 시나리오 직후 점수가 뚝 떨어지는 것은 의도한 동작이다. 실패해도 시나리오 종료는 막지 않는다.
     */
    private void refillWithCalmRain(DemoScenarioState.Run r) {
        try {
            long elapsedMs = System.currentTimeMillis() - r.startedAtMillis();
            Integer rows = tx.execute(status -> {
                LocalDateTime now = jdbc.queryForObject("SELECT LOCALTIMESTAMP", Timestamp.class).toLocalDateTime();
                LocalDateTime from = now.minusNanos(elapsedMs * 1_000_000L).minusMinutes(1).truncatedTo(ChronoUnit.MINUTES);
                int total = 0;
                for (Long areaId : r.areaIds()) {
                    jdbc.update("DELETE FROM weather WHERE area_id = ? AND observed_at >= ?", areaId, Timestamp.valueOf(from));
                    List<Object[]> batch = new ArrayList<>();
                    for (LocalDateTime t = from; !t.isAfter(now); t = t.plusMinutes(REFILL_STEP_MIN)) {
                        double rain = DemoCalmRain.rainPerObservation(areaId, t, REFILL_STEP_MIN);
                        double p = Math.min(1, rain * 60.0 / REFILL_STEP_MIN / 20.0);
                        double temp = 21 + (areaId % 4) + 2 * Math.sin(t.getHour() / 24.0 * 2 * Math.PI) - 2 * p;
                        int humidity = (int) Math.min(95, 55 + (areaId % 10) + 25 * p);
                        batch.add(new Object[] { areaId, Math.round(temp * 10) / 10.0, humidity,
                                Math.round(rain * 100) / 100.0, Timestamp.valueOf(t) });
                    }
                    for (int n : jdbc.batchUpdate("INSERT INTO weather (area_id, temp, humidity, rain, observed_at) "
                            + "VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING", batch)) {
                        total += Math.max(n, 0);
                    }
                }
                return total;
            });
            log.info("[demo] 시나리오 종료: 날씨 {}곳을 평상시 비로 다시 채움 ({}행)", r.areaIds().size(), rows);
        } catch (RuntimeException e) {
            log.warn("[demo] 시나리오 종료 날씨 되돌리기 실패: {}", e.toString());
        }
    }

    public DemoScenarioDtos.Status status() {
        DemoScenarioState.Run r = state.current();
        List<DemoScenarioDtos.Step> steps = new ArrayList<>();
        double speed = r == null ? 1.0 : r.speed();
        for (DemoScenarioState.Phase p : state.phases()) {
            steps.add(new DemoScenarioDtos.Step(p.name(), p.label, (int) Math.round(p.from / speed),
                    (int) Math.round(p.to / speed)));
        }
        if (r == null) {
            return new DemoScenarioDtos.Status(false, null, null, 0, (int) Math.round(DemoScenarioState.TOTAL_SEC),
                    List.of(), List.of(), 1.0, steps);
        }
        double sec = state.scenarioSec();
        DemoScenarioState.Phase phase = state.phaseAt(sec);
        return new DemoScenarioDtos.Status(true, phase.name(), phase.label, (int) Math.round(sec / speed),
                (int) Math.round(DemoScenarioState.TOTAL_SEC / speed), new ArrayList<>(r.regionIds()),
                new ArrayList<>(r.underpassIds()), speed, steps);
    }

    private static String csv(Set<Long> ids) {
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }
}