package com.mbc.puf.demo;

import java.util.Map;

/**
 * [시연용·Render] 평상시(버튼을 안 눌렀을 때) 지하차도별 위험점수·수위·강수 값.
 *
 * 로컬 시연 값(안전/관심/주의)을 그대로 이어받는다. 값은 지하차도마다 기준 점수(BASE_SCORE)가 있고,
 * 시각만으로 정해지는 부드러운 파동(난수 없음)으로 그 근처에서 조금씩 오르내린다.
 *   → 파동은 항상 같은 단계 안에서만 움직여서 단계가 바뀌지 않는다 = 이벤트·알림·전광판·푸시가 저절로 생기지 않는다.
 *
 * 점수·수위·강수는 단계에 맞춰 같이 정한다 (로컬 DemoLevelService / DemoRainTier 와 같은 기준):
 *   안전 : 수위 0.2cm · 비 1.5mm/h     관심 : 수위 1.9cm · 비 5mm/h     주의 : 수위 3.6cm · 비 13mm/h
 * 수위는 가장 낮은 통제 수위(5cm) 아래라 어느 단계에서도 자동 통제가 걸리지 않는다.
 *
 * 단계를 바꾸려면 BASE_SCORE 값을 바꾼다 (40 이상 관심, 70 이상 주의). 5.seed_demo SQL 의 시작 값과 맞춰 둘 것.
 */
final class DemoCalmProfile {

    /** 지하차도 id → 기준 점수 (5.seed_demo_v14.sql 의 demo_base 와 같다. 초량은 주의 72) */
    private static final Map<Long, Double> BASE_SCORE = Map.ofEntries(
            Map.entry(1L, 72.0), Map.entry(2L, 0.7), Map.entry(3L, 0.9), Map.entry(4L, 65.3), Map.entry(5L, 65.4),
            Map.entry(6L, 0.9), Map.entry(7L, 0.1), Map.entry(8L, 0.1), Map.entry(9L, 95.6), Map.entry(10L, 0.4),
            Map.entry(11L, 0.7), Map.entry(12L, 65.3), Map.entry(13L, 0.1), Map.entry(14L, 65.1), Map.entry(15L, 15.6),
            Map.entry(16L, 0.4), Map.entry(17L, 0.4), Map.entry(18L, 0.5), Map.entry(19L, 0.1), Map.entry(20L, 65.3),
            Map.entry(21L, 65.3), Map.entry(22L, 65.3), Map.entry(23L, 0.9), Map.entry(24L, 0.4), Map.entry(25L, 0.4),
            Map.entry(26L, 65.3), Map.entry(27L, 0.9), Map.entry(28L, 65.3), Map.entry(29L, 65.3), Map.entry(30L, 65.3),
            Map.entry(31L, 0.7), Map.entry(32L, 0.7), Map.entry(33L, 0.9), Map.entry(34L, 0.9), Map.entry(35L, 0.7),
            Map.entry(36L, 0.9), Map.entry(37L, 95.2), Map.entry(38L, 95.2), Map.entry(39L, 65.2), Map.entry(40L, 0.5),
            Map.entry(41L, 0.8), Map.entry(42L, 0.1), Map.entry(43L, 0.4), Map.entry(44L, 0.2), Map.entry(45L, 0.1));

    private static final double DEFAULT_SCORE = 0.5;          // 표에 없는 지하차도(새로 만든 곳)는 안전
    private static final double[] ACCUM = {1.0, 1.25, 1.3, 1.35, 1.4};            // 1h, 3h, 6h, 12h, 24h 누적 배율
    private static final double[] FORECAST = {1.0, 0.9, 0.75, 0.6, 0.45, 0.3};    // 앞으로 1~6시간 예보 배율
    private static final double MAX_ACCUM_MM = 50;

    private DemoCalmProfile() {
    }

    /** 0 안전 · 1 관심 · 2 주의 */
    static int band(long id) {
        double s = BASE_SCORE.getOrDefault(id, DEFAULT_SCORE);
        return s >= 70 ? 2 : s >= 40 ? 1 : 0;
    }

    private static double wave(long id, long epochSec, double periodSec) {
        return Math.sin(2 * Math.PI * epochSec / (periodSec + (id % 5) * 60) + id * 1.7);
    }

    /** 지금 보낼 위험점수: 기준 근처에서 같은 단계 안으로만 움직인다 */
    static double score(long id, long epochSec) {
        double base = BASE_SCORE.getOrDefault(id, DEFAULT_SCORE);
        int band = band(id);
        double w = wave(id, epochSec, 540);
        double v;
        if (band == 0) {
            v = base + 1.5 * (1 + w);                  // 안전: 기준 ~ 기준+3
        } else if (band == 1) {
            v = base + 1.8 * w;                        // 관심: ±1.8 (40~70 사이 한가운데라 안전)
        } else {
            v = base + (base < 80 ? 1.0 : 2.0) * w;   // 주의: 초량(72)은 ±1, 나머지(95대)는 ±2 → 70 아래로 안 내려감
        }
        return Math.round(Math.max(0, Math.min(100, v)) * 100) / 100.0;
    }

    /** 지금 보낼 수위(cm): 단계에 맞는 값 근처 (초량은 3.5cm 근처) */
    static double waterCm(long id, long epochSec) {
        int band = band(id);
        double base = band == 2 ? (id == 1 ? 3.5 : 3.6) : band == 1 ? 1.9 : 0.2;
        double amp = band == 0 ? 0.1 : 0.2;
        double v = base + amp * wave(id + 3, epochSec, 420);
        return Math.round(Math.max(0, v) * 10) / 10.0;
    }

    /** 지금 시간당 강수(mm/h): 단계에 맞는 값을 ±5% 흔든다 (수위 cm 보다 항상 크다) */
    static double rainMmH(long id, long epochSec) {
        int band = band(id);
        double base = band == 2 ? 13 : band == 1 ? 5 : 1.5;
        return Math.round(base * (1 + 0.05 * wave(id + 5, epochSec, 480)) * 10) / 10.0;
    }

    /** {1시간, 3시간, 6시간, 12시간, 24시간} 누적 (각각 50mm 이하) */
    static double[] accumulations(double nowMm) {
        double[] out = new double[ACCUM.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = Math.round(Math.min(MAX_ACCUM_MM, nowMm * ACCUM[i]) * 10) / 10.0;
        }
        return out;
    }

    /** hoursAhead(1부터) 시간 뒤 예보 강수(mm/h) */
    static double forecastMm(double nowMm, int hoursAhead) {
        int i = Math.max(0, Math.min(FORECAST.length - 1, hoursAhead - 1));
        return Math.round(nowMm * FORECAST[i] * 10) / 10.0;
    }
}
