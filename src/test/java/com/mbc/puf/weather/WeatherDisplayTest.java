package com.mbc.puf.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.weather.dto.WeatherResponse;
import com.mbc.puf.weather.entity.WeatherArea;
import com.mbc.puf.weather.repository.WeatherAreaRepository;
import com.mbc.puf.weather.repository.WeatherForecastRepository;
import com.mbc.puf.weather.repository.WeatherRepository;
import com.mbc.puf.weather.service.KmaClient;
import com.mbc.puf.weather.service.KmaWeatherStore;
import com.mbc.puf.weather.service.WeatherServiceImpl;

import jakarta.persistence.EntityManager;

/** 화면 날씨(getWeather)가 DB 가 아니라 메모리(KmaWeatherStore)만 읽는지 확인한다 */
class WeatherDisplayTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 24, 14, 20);

    private final KmaWeatherStore store = new KmaWeatherStore();
    private final WeatherRepository weatherRepository = mock(WeatherRepository.class);          // 침수모델용 stub 테이블
    private final WeatherForecastRepository forecastRepository = mock(WeatherForecastRepository.class);
    private final WeatherServiceImpl service;
    private final WeatherArea area = new WeatherArea();

    WeatherDisplayTest() {
        WeatherAreaRepository areaRepository = mock(WeatherAreaRepository.class);
        DbClock clock = mock(DbClock.class);
        when(clock.now()).thenReturn(NOW);
        when(areaRepository.findNearest(any(), any(), any(Limit.class))).thenReturn(List.of(area));
        area.setName("중구");
        try {   // id 는 DB 가 붙이는 값이라 테스트에서는 리플렉션으로 넣는다
            var f = WeatherArea.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(area, 1L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        service = new WeatherServiceImpl(areaRepository, weatherRepository, forecastRepository, store, clock,
                mock(EntityManager.class));
    }

    private WeatherResponse get() {
        return service.getWeather(new BigDecimal("35.1"), new BigDecimal("129.0"));
    }

    @Test
    void emptyStoreGivesBlankCurrentWeather() {
        WeatherResponse r = get();
        assertThat(r.getTemperature()).isNull();
        assertThat(r.getHourly()).isEmpty();
        assertThat(r.getDaily()).isEmpty();
    }

    @Test
    void storeValuesAreShownAndStubTablesAreNeverRead() {
        store.putObservation(1L, new KmaClient.Observation(new BigDecimal("24.3"), 91, new BigDecimal("12.5"),
                LocalDateTime.of(2026, 7, 24, 13, 0)));
        store.putForecast(1L, List.of(
                new KmaClient.ForecastPoint(LocalDateTime.of(2026, 7, 24, 14, 0), new BigDecimal("23.0"), 80, BigDecimal.ZERO, null, "흐림"),
                new KmaClient.ForecastPoint(LocalDateTime.of(2026, 7, 24, 15, 0), new BigDecimal("24.0"), 90, new BigDecimal("3.0"), null, "비"),
                new KmaClient.ForecastPoint(LocalDateTime.of(2026, 7, 24, 16, 0), new BigDecimal("26.0"), 85, BigDecimal.ZERO, null, "흐림"),
                new KmaClient.ForecastPoint(LocalDateTime.of(2026, 7, 25, 0, 0), new BigDecimal("21.0"), 70, BigDecimal.ZERO, null, "맑음")));
        WeatherResponse r = get();
        assertThat(r.getTemperature()).isEqualByComparingTo("24.3");
        assertThat(r.getHumidity()).isEqualTo(91);
        assertThat(r.getCondition()).isEqualTo("소나기");              // 12.5mm
        assertThat(r.getObservedAt()).isEqualTo("2026-07-24 13:00");
        assertThat(r.getHourly()).extracting(WeatherResponse.Hour::getTime).containsExactly("15시", "16시", "0시");   // 14시는 지난 시점
        assertThat(r.getDaily()).hasSize(2);
        assertThat(r.getDaily().get(0).getMinTemperature()).isEqualByComparingTo("24.0");
        assertThat(r.getDaily().get(0).getMaxTemperature()).isEqualByComparingTo("26.0");
        assertThat(r.getDaily().get(0).getPrecipitationProbability()).isNull();
        org.mockito.Mockito.verifyNoInteractions(weatherRepository, forecastRepository);
    }

    @Test
    void staleObservationIsNotShownAsCurrent() {
        store.putObservation(1L, new KmaClient.Observation(new BigDecimal("20.0"), 60, BigDecimal.ZERO,
                LocalDateTime.of(2026, 7, 24, 9, 0)));   // 5시간 전
        assertThat(get().getTemperature()).isNull();
    }

    @Test
    void emptyForecastKeepsPreviousOne() {
        store.putForecast(1L, List.of(new KmaClient.ForecastPoint(LocalDateTime.of(2026, 7, 24, 15, 0),
                new BigDecimal("24.0"), 90, BigDecimal.ZERO, null, "흐림")));
        store.putForecast(1L, List.of());   // 기상청 실패로 빈 목록이 와도 이전 예보를 지우지 않는다
        assertThat(get().getHourly()).hasSize(1);
    }
}
