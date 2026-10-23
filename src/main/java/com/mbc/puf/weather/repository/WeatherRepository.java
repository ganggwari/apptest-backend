package com.mbc.puf.weather.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.weather.entity.Weather;

public interface WeatherRepository extends JpaRepository<Weather, Long> {

    // 현재 날씨: now 까지의 관측값 중 가장 최근 1건
    List<Weather> findByAreaIdAndObservedAtLessThanEqualOrderByObservedAtDesc(Long areaId, LocalDateTime now, Limit limit);

    // 같은 지역+관측시각이 있으면 값을 덮어쓰고, 없으면 새로 넣는다 (수집이 겹쳐도 중복 오류가 나지 않게)
    @Modifying
    @Query(value = """
            INSERT INTO weather (area_id, temp, humidity, rain, observed_at)
            VALUES (:areaId, :temp, :humidity, :rain, :observedAt)
            ON CONFLICT (area_id, observed_at)
            DO UPDATE SET temp = EXCLUDED.temp, humidity = EXCLUDED.humidity, rain = EXCLUDED.rain
            """, nativeQuery = true)
    int upsert(@Param("areaId") Long areaId, @Param("temp") java.math.BigDecimal temp,
               @Param("humidity") Integer humidity, @Param("rain") java.math.BigDecimal rain,
               @Param("observedAt") LocalDateTime observedAt);

    // 오래된 관측값 정리 (누적이 계속 커지지 않게)
    @Modifying
    @Query("delete from Weather w where w.observedAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
