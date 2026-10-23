package com.mbc.puf.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.weather.entity.WeatherArea;
import com.mbc.puf.weather.service.KmaClient;

/**
 * 기상청 API 흉내 (시연 전용). 평소에는 잔잔한 날씨를, 호우 시나리오 중인 지역에는 시나리오 곡선대로 센 비를 준다.
 * 실제 기상청 연동을 붙일 때는 이 클래스 대신 실제 KmaClient 구현을 쓰면 된다.
 */
@Component
@ConditionalOnProperty(name = "puf.demo.enabled", havingValue = "true")
public class DemoKmaClient implements KmaClient {

    private static final int CALM_FORECAST_HOURS = 72;
    private static final int RAIN_FORECAST_HOURS = 24;

    private final DemoScenarioState state;
    private final DbClock dbClock;

    public DemoKmaClient(DemoScenarioState state, DbClock dbClock) {
        this.state = state;
        this.dbClock = dbClock;
    }

    private static BigDecimal dec(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }

    @Override
    public Observation fetchObservation(WeatherArea area) {
        LocalDateTime now = dbClock.now().truncatedTo(ChronoUnit.SECONDS);
        long id = area.getId();
        double temp = 21 + (id % 4) + 2 * Math.sin(now.getHour() / 24.0 * 2 * Math.PI);
        int humidity = 55 + (int) (id % 10);
        double rain = 0;

        if (state.isAreaActive(area.getId())) {
            double t = state.scenarioSec();
            double factor = 0.9 + (id % 5) * 0.05;
            rain = state.rainAt(t) * factor;
            double p = Math.min(1, rain / 9.0);
            temp -= 3 * p;
            humidity = (int) Math.min(98, humidity + 30 * p);
        }
        return new Observation(dec(temp, 1), humidity, dec(rain, 2), now);
    }

    @Override
    public List<ForecastPoint> fetchForecast(WeatherArea area) {
        LocalDateTime base = dbClock.now().truncatedTo(ChronoUnit.HOURS);
        boolean active = state.isAreaActive(area.getId());
        double scale = active ? state.forecastScaleAt(state.scenarioSec()) : 0;
        int hours = active ? RAIN_FORECAST_HOURS : CALM_FORECAST_HOURS;
        long id = area.getId();

        List<ForecastPoint> list = new ArrayList<>();
        for (int h = 1; h <= hours; h++) {
            LocalDateTime target = base.plusHours(h);
            double temp = 21 + (id % 4) + 3 * Math.sin((target.getHour() - 9) / 24.0 * 2 * Math.PI);
            int humidity = 55 + (int) ((id + h) % 12);
            double rain = active ? state.forecastShape(h) * scale : 0;
            int prob;
            String sky;
            if (rain >= 1) {
                prob = (int) Math.min(100, 50 + rain);
                sky = rain >= 20 ? "비" : "흐림";
                humidity = Math.min(98, humidity + 30);
                temp -= 3;
            } else {
                prob = (int) (5 + (id + h) % 15);
                sky = (id + h) % 3 == 0 ? "구름많음" : "맑음";
            }
            list.add(new ForecastPoint(target, dec(temp, 1), humidity, dec(rain, 2), prob, sky));
        }
        return list;
    }
}
