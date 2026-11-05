package com.mbc.puf.demo;

import com.mbc.puf.common.enums.RiskLevel;

/**
 * 위험단계 고정(DemoLevelService) 때 같이 보여줄 "그 단계에 어울리는 비"를 정한다 (시연 전용).
 * 세기 기준(시간당): 1~3mm 약한 비, 3~10mm 보통 비, 10~20mm 강한 비, 20~30mm 매우 강한 비, 30~50mm 집중호우.
 *   관심 : 보통 비        5 mm/h
 *   주의 : 강한 비       13 mm/h
 *   경계 : 매우 강한 비  24 mm/h
 *   심각 : 집중호우      36 mm/h   (일부러 50 을 넘지 않게 한다)
 * 수위(cm)는 항상 이 비(mm)보다 낮게 나오도록 값을 잡았다 (심각 단계 수위는 통제 수위 15cm 지하차도에서 30.5cm).
 * 안전 단계는 평상시 비(DemoCalmRain)를 그대로 쓰므로 여기서는 0 을 돌려준다(= 덮어쓰지 않는다).
 */
final class DemoRainTier {

    static final double MAX_ACCUM_MM = 50;    // 누적 강수량이 이 값을 넘지 않게 한다
    // 비가 시작된 지 얼마 안 된 상황을 가정한 누적 배율 (1시간 = 현재 세기 그대로)
    private static final double[] ACCUM = {1.0, 1.25, 1.3, 1.35, 1.4};            // 1h, 3h, 6h, 12h, 24h
    private static final double[] FORECAST = {1.0, 0.9, 0.75, 0.6, 0.45, 0.3};    // 앞으로 1~6시간 예보 배율

    private DemoRainTier() {
    }

    /** 단계별 시간당 강수 세기(mm/h). 안전이면 0 (덮어쓰지 않음) */
    static double intensity(RiskLevel level) {
        if (level == null) {
            return 0;
        }
        return switch (level) {
            case WATCH -> 5;
            case CAUTION -> 13;
            case ALERT -> 24;
            case SEVERE -> 36;
            default -> 0;
        };
    }

    /** {1시간, 3시간, 6시간, 12시간, 24시간} 누적 (각각 50mm 이하) */
    static double[] accumulations(double intensity) {
        double[] out = new double[ACCUM.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = Math.min(MAX_ACCUM_MM, intensity * ACCUM[i]);
        }
        return out;
    }

    /** hoursAhead(1부터) 시간 뒤 예보 강수량 (mm/h). 시간이 갈수록 서서히 약해진다 */
    static double forecast(double intensity, int hoursAhead) {
        int i = Math.max(0, Math.min(FORECAST.length - 1, hoursAhead - 1));
        return intensity * FORECAST[i];
    }
}
