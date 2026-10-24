package com.mbc.puf.weather.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mbc.puf.weather.client.KmaApiClient;
import com.mbc.puf.weather.entity.WeatherArea;
import com.mbc.puf.weather.repository.WeatherAreaRepository;

/**
 * 실제 기상청 날씨를 주기적으로 받아 메모리(KmaWeatherStore)에 둔다 (프런트 날씨 화면 표출 전용).
 * 시연 stub(puf.demo.enabled)과는 상관없이 돈다: stub 이 켜져 있어도 침수모델 쪽 weather / weather_forecast 는
 * DemoStubScheduler 가 그대로 채우고, 이 스케줄러는 DB 에 아무것도 쓰지 않는다.
 * 기상청 자료는 한 시간에 한 번 바뀌므로 자주 돌려도 같은 격자·같은 발표 시각은 다시 호출하지 않는다 (KmaApiClient 가 기억).
 */
@Component
@ConditionalOnProperty(name = "puf.kma.collect-enabled", havingValue = "true", matchIfMissing = true)
public class KmaDisplayScheduler {

    private static final Logger log = LoggerFactory.getLogger(KmaDisplayScheduler.class);

    private final KmaApiClient kmaApiClient;
    private final KmaWeatherStore store;
    private final WeatherAreaRepository areaRepository;

    public KmaDisplayScheduler(KmaApiClient kmaApiClient, KmaWeatherStore store, WeatherAreaRepository areaRepository) {
        this.kmaApiClient = kmaApiClient;
        this.store = store;
        this.areaRepository = areaRepository;
    }

    @Scheduled(initialDelayString = "#{${puf.kma.collect-initial-delay-seconds:10} * 1000}",
            fixedDelayString = "#{${puf.kma.collect-interval-minutes:10} * 60 * 1000}")
    public void collect() {
        try {
            List<WeatherArea> areas = areaRepository.findAll();
            int observed = 0;
            int forecasted = 0;
            for (WeatherArea area : areas) {
                try {
                    KmaClient.Observation observation = kmaApiClient.fetchObservation(area);
                    if (observation != null) {
                        store.putObservation(area.getId(), observation);
                        observed++;
                    }
                    List<KmaClient.ForecastPoint> forecast = kmaApiClient.fetchForecast(area);
                    if (!forecast.isEmpty()) {
                        store.putForecast(area.getId(), forecast);
                        forecasted++;
                    }
                } catch (RuntimeException e) {
                    log.warn("[kma] 지역 수집 실패: area={} ({})", area.getName(), e.toString());
                }
            }
            log.info("[kma] 표출용 수집 완료: 지역 {}곳, 관측 {}곳, 예보 {}곳", areas.size(), observed, forecasted);
        } catch (RuntimeException e) {
            log.warn("[kma] 수집 실패: {}", e.toString());
        }
    }
}
