package com.mbc.puf.demo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.stream.StreamPublisher;

/** 호우 시나리오 시작/중지/상태 조회 (시연 전용) */
@Service
@ConditionalOnProperty(name = "puf.demo.enabled", havingValue = "true")
public class DemoScenarioService {

    private final DemoScenarioState state;
    private final JdbcTemplate jdbc;
    private final StreamPublisher streamPublisher;

    public DemoScenarioService(DemoScenarioState state, JdbcTemplate jdbc, StreamPublisher streamPublisher) {
        this.state = state;
        this.jdbc = jdbc;
        this.streamPublisher = streamPublisher;
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
        if (state.current() == null) {
            return;
        }
        state.end();
        streamPublisher.dataChangedPublic(null, "demo-scenario", "UPDATE", null);
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
