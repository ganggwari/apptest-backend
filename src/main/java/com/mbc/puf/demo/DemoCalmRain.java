package com.mbc.puf.demo;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 평상시(호우 시나리오가 아닐 때) 시연용 날씨 (Render). 약한 비가 조금씩 오르내리며 계속 온다.
 * 예전에는 5~8시간마다 소나기(최고 18~38mm/h)가 지나갔는데, 그러면 위험점수·수위와 강수가 따로 놀아서
 * 버튼을 안 눌러도 단계가 오르내리고 알림이 생겼다. 이제는 소나기 없이 1~2mm/h 안에서만 움직인다.
 * (지하차도별 위험점수·수위·강수는 DemoCalmProfile 이 단계에 맞춰 따로 정한다. 여기는 날씨 지역 관측·예보용)
 * 시각만으로 값이 정해지므로(난수 없음) 서버를 껐다 켜도, 과거 날씨를 채울 때도 같은 곡선이 이어진다.
 */
public final class DemoCalmRain {

    private static final double BASE_MM = 1.0;          // 기본 세기 mm/h
    private static final double SPAN_MM = 1.0;          // 지역별 추가분 (1.0 ~ 2.0)
    private static final double WOBBLE = 0.25;          // 시간에 따른 흔들림 폭 (±25%)

    private DemoCalmRain() {
    }

    /** 그 시각 그 지역의 강수 세기 (mm/h) */
    public static double intensity(long areaId, LocalDateTime t) {
        double hours = t.toEpochSecond(ZoneOffset.UTC) / 3600.0;
        double base = BASE_MM + ((areaId * 37) % 10) / 10.0 * SPAN_MM;
        double period = 2.5 + (areaId % 3) * 0.5;
        return base * (1 + WOBBLE * Math.sin(2 * Math.PI * hours / period + areaId));
    }

    /** 관측 한 건(minutes 분 간격)에 쌓일 강수량 (mm) */
    public static double rainPerObservation(long areaId, LocalDateTime t, int minutes) {
        return intensity(areaId, t) * minutes / 60.0;
    }
}
