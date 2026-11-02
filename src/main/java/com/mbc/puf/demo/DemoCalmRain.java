package com.mbc.puf.demo;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 평상시(호우 시나리오가 아닐 때) 시연용 날씨. "언제나 비가 조금씩 오는" 상태를 만든다.
 * 지역마다 아주 약한 이슬비가 깔려 있고, 8~12시간마다 지역별로 시각이 다른 보통 세기의 소나기가 한 번씩 지나간다.
 * 세기 기준(시간당): 1~3mm 약한 비, 3~10mm 보통 비, 10~20mm 강한 비, 20~30mm 매우 강한 비, 30mm 이상 집중호우. 소나기는 최고 약 4~12mm 라서 보통~강한 비 정도다.
 * 시각만으로 값이 정해지므로(난수 없음) 서버를 껐다 켜도, 과거 날씨를 채울 때도 같은 곡선이 이어진다.
 * 예전에는 소나기가 최고 18~38mm/h(매우 강한 비 ~ 집중호우)가 5~8시간마다 와서 평상시에도 점수·수위가 자꾸 올랐다. 지금은 훨씬 약하게 낮췄다.
 * 소나기를 더 세게·넓게 하려면 SHOWER_BASE, SHOWER_STEP, SHOWER_WIDTH_H 를 키운다.
 */
public final class DemoCalmRain {

    private static final double DRIZZLE_MIN = 0.1;      // 이슬비 mm/h (지역별로 0.1 ~ 0.7)
    private static final double DRIZZLE_SPAN = 0.6;
    private static final int PERIOD_BASE_H = 8;         // 소나기 간격 8 ~ 12시간
    private static final int PERIOD_SPAN_H = 5;
    private static final double SHOWER_BASE = 4;        // 소나기 최고 세기 mm/h (지역별로 4 ~ 12)
    private static final double SHOWER_STEP = 2;
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
