package com.mbc.puf.weather.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.weather.entity.WeatherArea;

public interface WeatherAreaRepository extends JpaRepository<WeatherArea, Long> {

    // 위경도에서 가장 가까운 날씨 지역 (지역 수가 적어서 단순 거리 제곱으로 비교)
    @Query("select a from WeatherArea a order by (a.latitude - :lat) * (a.latitude - :lat)"
            + " + (a.longitude - :lng) * (a.longitude - :lng)")
    List<WeatherArea> findNearest(@Param("lat") BigDecimal lat, @Param("lng") BigDecimal lng, Limit limit);
}
