package com.mbc.puf.demo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 호우 시나리오의 진행 상태와 곡선(시간에 따른 비·수위·예보 크기)을 가진다. 시연 전용 (puf.demo.enabled=true 일 때만).
 *
 * 시간 규칙: 시나리오 시간은 "시작 버튼을 누른 뒤 실제로 흐른 초 × speed" 이다.
 * DB 시계·알림·내비 시각은 건드리지 않고 그대로 실제 시계를 쓴다 (가속 시계를 쓰면 다른 시연이 꼬이므로).
 */
@Component
@ConditionalOnProperty(name = "puf.demo.enabled", havingValue = "true")
public class DemoScenarioState {

    /** 시나리오 전체 길이 (speed=1 일 때 초) */
    public static final double TOTAL_SEC = 120;

    /** 진행 단계 (시나리오 시간 기준 구간) */
    public enum Phase {
        FORECAST("호우 예보", 0, 10),
        RISING("강우 시작 · 위험도 상승", 10, 60),
        BLOCKING("수위 상승 · 자동 차단", 60, 100),
        RECOVERING("비 약화 · 수위 하강", 100, 120);

        public final String label;
        public final double from;
        public final double to;

        Phase(String label, double from, double to) {
            this.label = label;
            this.from = from;
            this.to = to;
        }
    }

    /** 실행 중인 시나리오 정보 */
    public record Run(Set<Long> regionIds, Set<Long> underpassIds, Set<Long> areaIds, long startedAtMillis,
                      double speed) {}

    private volatile Run run;
    private volatile Phase lastPublishedPhase;

    // 지하차도별 위험점수 (모델 흉내의 "기억"). 비가 그치면 서서히 내려가게 한다
    private final Map<Long, Double> scores = new ConcurrentHashMap<>();

    public Run current() {
        return run;
    }

    public void begin(Run newRun) {
        scores.clear();
        lastPublishedPhase = null;
        run = newRun;
    }

    private final java.util.concurrent.atomic.AtomicBoolean resetPending = new java.util.concurrent.atomic.AtomicBoolean();

    /** 시나리오가 끝났거나 중지돼서, 다음 틱에 전체를 평소 값으로 되돌려야 하는지 (읽으면 꺼진다) */
    public boolean takeReset() {
        return resetPending.getAndSet(false);
    }

    public void end() {
        resetPending.set(true);
        run = null;
        scores.clear();
        lastPublishedPhase = null;
    }

    public Phase lastPublishedPhase() {
        return lastPublishedPhase;
    }

    public void markPublished(Phase phase) {
        lastPublishedPhase = phase;
    }

    /** 시나리오 시간(초). 실행 중이 아니면 -1 */
    public double scenarioSec() {
        Run r = run;
        if (r == null) {
            return -1;
        }
        return (System.currentTimeMillis() - r.startedAtMillis()) / 1000.0 * r.speed();
    }

    public boolean finished() {
        return run != null && scenarioSec() >= TOTAL_SEC;
    }

    public Phase phaseAt(double sec) {
        for (Phase p : Phase.values()) {
            if (sec < p.to) {
                return p;
            }
        }
        return Phase.RECOVERING;
    }

    public boolean isAreaActive(Long areaId) {
        Run r = run;
        return r != null && r.areaIds().contains(areaId);
    }

    public boolean isUnderpassActive(Long underpassId) {
        Run r = run;
        return r != null && r.underpassIds().contains(underpassId);
    }

    // ---------------- 곡선 (시나리오 시간 → 값) ----------------

    private static double interpolate(double t, double[][] points) {
        if (t <= points[0][0]) {
            return points[0][1];
        }
        for (int i = 0; i + 1 < points.length; i++) {
            double a = points[i][0], b = points[i + 1][0];
            if (t <= b) {
                return points[i][1] + (points[i + 1][1] - points[i][1]) * (t - a) / (b - a);
            }
        }
        return points[points.length - 1][1];
    }

    // 이 아래 숫자 표가 시나리오의 "시간 → 값" 곡선이다. {시나리오 초, 값} 쌍이고, 쌍 사이는 직선으로 이어진다.
    // 실제로 내리는 비(관측): 처음 20초는 안 오다가 서서히 세진다 (예보가 먼저 알리고, 실제 비는 뒤따라온다)
    private static final double[][] RAIN = {{0, 0}, {10, 0}, {30, 2}, {55, 6}, {75, 9}, {98, 9}, {120, 0}};
    // 수위(cm): 천천히 오르다가 60초 이후에 빨라진다 (최대 18cm, 2분 시나리오). 5cm 지하차도는 약 68초, 15cm 지하차도는 약 88초에 기준에 닿는다
    private static final double[][] WATER = {{0, 0.2}, {25, 0.4}, {45, 1.5}, {60, 3}, {70, 5.5}, {80, 9}, {88, 15},
            {96, 18}, {105, 18}, {114, 6}, {120, 0}};
    // 예보 크기 비율: 시나리오 앞쪽에는 1(예보가 가장 센 상태)을 유지하고, 비가 약해지는 구간에서 서서히 줄인다
    private static final double[][] FORECAST_SCALE = {{0, 1}, {90, 1}, {120, 0.15}};
    // 시간별 예보 강수량(mm/h) 모양: 지금부터 1시간 뒤, 2시간 뒤 ... 순서. 평소 예보는 0이라서 시작하자마자 급격히 센 비를 알린다 (3시간 뒤 105mm/h 가 최대)
    private static final double[] FORECAST_SHAPE = {45, 80, 105, 80, 50, 28, 12, 4};

    /** 이번 한 번(10초)의 강수량(mm). 시나리오 시간 t 기준 */
    public double rainAt(double t) {
        return interpolate(t, RAIN);
    }

    /** 시나리오 수위(cm) */
    public double waterAt(double t) {
        return interpolate(t, WATER);
    }

    /** 예보 크기 비율 (비가 약해지면 예보도 서서히 줄어든다) */
    public double forecastScaleAt(double t) {
        return interpolate(t, FORECAST_SCALE);
    }

    /** hoursAhead(1부터) 시간 뒤의 예보 강수량(mm/h) 기본 모양 */
    public double forecastShape(int hoursAhead) {
        int i = hoursAhead - 1;
        return i >= 0 && i < FORECAST_SHAPE.length ? FORECAST_SHAPE[i] : 0;
    }

    /** 위험점수 한 칸 갱신: 목표(비에 따른 값)를 향해 조금씩 움직인다 (올라갈 때도 내려갈 때도 서서히) */
    public double nextScore(Long underpassId, double target, double baseline) {
        double current = scores.getOrDefault(underpassId, baseline);
        double next = current + 0.35 * (Math.max(target, baseline) - current);
        scores.put(underpassId, next);
        return next;
    }

    public void resetScore(Long underpassId) {
        scores.remove(underpassId);
    }

    /** 사용자에게 보여줄 단계 목록 (실제 초 기준) */
    public List<Phase> phases() {
        return List.of(Phase.values());
    }
}
