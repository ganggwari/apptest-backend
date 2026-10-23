package com.mbc.puf.weather.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.weather.entity.WeatherForecast;

public interface WeatherForecastRepository extends JpaRepository<WeatherForecast, Long> {

    // 시간별 예보: now 이후 예보 N건 (시간 오름차순)
    List<WeatherForecast> findByAreaIdAndTargetAtGreaterThanOrderByTargetAtAsc(Long areaId, LocalDateTime now, Limit limit);

    // 일별 예보: 날짜별로 묶어 [날짜, 최저기온, 최고기온, 최대 강수확률] (날짜 오름차순)
    @Query("select cast(w.targetAt as LocalDate), min(w.temperatureC), max(w.temperatureC), max(w.precipitationProb)"
            + " from WeatherForecast w where w.areaId = :areaId and w.targetAt >= :from"
            + " group by cast(w.targetAt as LocalDate) order by cast(w.targetAt as LocalDate)")
    List<Object[]> findDailyForecast(@Param("areaId") Long areaId, @Param("from") LocalDateTime from, Limit limit);

    // 같은 지역+대상시각은 덮어쓴다
    @Modifying
    @Query(value = """
            INSERT INTO weather_forecast (area_id, temp, humidity, rain, rain_prob, sky, target_at, predicted_at)
            VALUES (:areaId, :temp, :humidity, :rain, :rainProb, :sky, :targetAt, :predictedAt)
            ON CONFLICT (area_id, target_at)
            DO UPDATE SET temp = EXCLUDED.temp, humidity = EXCLUDED.humidity, rain = EXCLUDED.rain,
                          rain_prob = EXCLUDED.rain_prob, sky = EXCLUDED.sky, predicted_at = EXCLUDED.predicted_at
            """, nativeQuery = true)
    int upsert(@Param("areaId") Long areaId, @Param("temp") BigDecimal temp, @Param("humidity") Integer humidity,
               @Param("rain") BigDecimal rain, @Param("rainProb") Integer rainProb, @Param("sky") String sky,
               @Param("targetAt") LocalDateTime targetAt, @Param("predictedAt") LocalDateTime predictedAt);

    // 지난 시점 예보 삭제
    @Modifying
    @Query("delete from WeatherForecast w where w.targetAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
