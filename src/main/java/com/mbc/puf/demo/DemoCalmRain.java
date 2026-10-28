package com.mbc.puf.demo;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 평상시(호우 시나리오가 아닐 때) 시연용 날씨. "언제나 비가 조금씩 오는" 상태를 만든다.
 * 지역마다 약한 이슬비가 깔려 있고, 5~8시간마다 지역별로 시각이 다른 소나기가 한 번씩 지나간다.
 * 시각만으로 값이 정해지므로(난수 없음) 서버를 껐다 켜도, 과거 날씨를 채울 때도 같은 곡선이 이어진다.
 * 침수모델(실제 침수 서버)로 지하차도 45곳을 돌려 맞춘 값이다: 안전 약 60% / 관심 약 25% / 주의 약 15%.
 * 소나기를 더 세게·넓게 하려면 SHOWER_BASE, SHOWER_STEP, SHOWER_WIDTH_H 를 키운다.
 */
public final class DemoCalmRain {

    private static final double DRIZZLE_MIN = 0.3;      // 이슬비 mm/h (지역별로 0.3 ~ 1.8)
    private static final double DRIZZLE_SPAN = 1.5;
    private static final int PERIOD_BASE_H = 5;         // 소나기 간격 5 ~ 8시간
    private static final int PERIOD_SPAN_H = 4;
    private static final double SHOWER_BASE = 18;       // 소나기 최고 세기 mm/h (지역별로 18 ~ 38)
    private static final double SHOWER_STEP = 5;
    private static final double SHOWER_WIDTH_H = 1.0;   // 소나기 폭(시간)

    private DemoCalmRain() {
    }

    /** 그 시각 그 지역의 강수 세기 (mm/h) */
    public static double intensity(long areaId, LocalDateTime t) {
        double hours = t.toEpochSecond(ZoneOffset.UTC) / 3600.0;
        double drizzle = DRIZZLE_MIN + ((areaId * 37) % 10) / 10.0 * DRIZZLE_SPAN;
        double period = PERIOD_BASE_H + (areaId * 7) % PERIOD_SPAN_H;
        double phase = (areaId * 2.3) % period;
        double amp = SHOWER_BASE + ((areaId * 11) % 5) * SHOWER_STEP;
        double k = (hours - phase) / period;
        double x = (k - Math.floor(k) - 0.5) * period;
        return drizzle + amp * Math.exp(-(x / SHOWER_WIDTH_H) * (x / SHOWER_WIDTH_H));
    }

    /** 관측 한 건(minutes 분 간격)에 쌓일 강수량 (mm) */
    public static double rainPerObservation(long areaId, LocalDateTime t, int minutes) {
        return intensity(areaId, t) * minutes / 60.0;
    }
}
