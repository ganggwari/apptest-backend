package com.mbc.puf.weather.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.weather.entity.WeatherArea;
import com.mbc.puf.weather.repository.WeatherAreaRepository;
import com.mbc.puf.weather.repository.WeatherForecastRepository;
import com.mbc.puf.weather.repository.WeatherRepository;

/**
 * 기상청(KmaClient)에서 받은 값을 weather / weather_forecast 에 저장한다.
 * 관측값은 계속 쌓고(오래된 것만 정리), 예보는 같은 시점이면 덮어쓴 뒤 지난 시점 행을 지운다.
 */
@Service
public class WeatherCollectService {

    private static final int KEEP_OBSERVATION_DAYS = 2;

    private final WeatherAreaRepository areaRepository;
    private final WeatherRepository weatherRepository;
    private final WeatherForecastRepository forecastRepository;
    private final DbClock dbClock;

    public WeatherCollectService(WeatherAreaRepository areaRepository, WeatherRepository weatherRepository,
                                 WeatherForecastRepository forecastRepository, DbClock dbClock) {
        this.areaRepository = areaRepository;
        this.weatherRepository = weatherRepository;
        this.forecastRepository = forecastRepository;
        this.dbClock = dbClock;
    }

    @Transactional(readOnly = true)
    public List<WeatherArea> allAreas() {
        return areaRepository.findAll();
    }

    /** 현재 관측값 한 건 저장 */
    @Transactional
    public void saveObservation(WeatherArea area, KmaClient.Observation o) {
        if (o == null) {
            return;
        }
        weatherRepository.upsert(area.getId(), o.temp(), o.humidity(), o.rain(), o.observedAt());
    }

    /** 예보 여러 건 저장 (같은 시점은 덮어쓰기) + 지난 시점 예보 삭제 */
    @Transactional
    public void saveForecast(WeatherArea area, List<KmaClient.ForecastPoint> points) {
        LocalDateTime now = dbClock.now();
        for (KmaClient.ForecastPoint p : points) {
            forecastRepository.upsert(area.getId(), p.temp(), p.humidity(), p.rain(), p.rainProb(), p.sky(),
                    p.targetAt(), now);
        }
        forecastRepository.deleteOlderThan(now.minusHours(1));
    }

    /** 오래된 관측값 정리 */
    @Transactional
    public void purgeOldObservations() {
        weatherRepository.deleteOlderThan(dbClock.now().minusDays(KEEP_OBSERVATION_DAYS));
    }
}
