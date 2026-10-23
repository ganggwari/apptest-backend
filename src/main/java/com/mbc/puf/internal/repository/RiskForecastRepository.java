package com.mbc.puf.internal.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.internal.entity.RiskForecast;

public interface RiskForecastRepository extends JpaRepository<RiskForecast, Long> {

    // 지금 이후 시점의 예측 (시간 오름차순)
    List<RiskForecast> findByUnderpassIdAndTargetAtGreaterThanEqualOrderByTargetAtAsc(Long underpassId, LocalDateTime from);

    // 같은 지하차도+대상시점이 있으면 덮어쓰고 없으면 넣는다
    @Modifying
    @Query(value = """
            INSERT INTO risk_forecast (underpass_id, score, rain, rain_1h, rain_3h, rain_6h, rain_12h, rain_24h,
                                       target_at, predicted_at)
            VALUES (:underpassId, :score, :rain, :rain1h, :rain3h, :rain6h, :rain12h, :rain24h, :targetAt, :predictedAt)
            ON CONFLICT (underpass_id, target_at)
            DO UPDATE SET score = EXCLUDED.score, rain = EXCLUDED.rain, rain_1h = EXCLUDED.rain_1h,
                          rain_3h = EXCLUDED.rain_3h, rain_6h = EXCLUDED.rain_6h, rain_12h = EXCLUDED.rain_12h,
                          rain_24h = EXCLUDED.rain_24h, predicted_at = EXCLUDED.predicted_at
            """, nativeQuery = true)
    int upsert(@Param("underpassId") Long underpassId, @Param("score") BigDecimal score,
               @Param("rain") BigDecimal rain, @Param("rain1h") BigDecimal rain1h, @Param("rain3h") BigDecimal rain3h,
               @Param("rain6h") BigDecimal rain6h, @Param("rain12h") BigDecimal rain12h,
               @Param("rain24h") BigDecimal rain24h, @Param("targetAt") LocalDateTime targetAt,
               @Param("predictedAt") LocalDateTime predictedAt);

    // 지난 시점 행 삭제
    @Modifying
    @Query("delete from RiskForecast f where f.underpassId = :underpassId and f.targetAt < :before")
    int deletePast(@Param("underpassId") Long underpassId, @Param("before") LocalDateTime before);
}
