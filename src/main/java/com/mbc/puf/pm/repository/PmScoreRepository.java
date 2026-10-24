package com.mbc.puf.pm.repository;

import com.mbc.puf.pm.entity.PmScore;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PmScoreRepository extends JpaRepository<PmScore, Long> {

    /** 점수 이력 한 점 (이벤트 트리거 판단용: 직전 점수, 24시간 최저) */
    interface ScorePoint {
        Long getEquipId();
        LocalDateTime getPredictedAt();
        BigDecimal getScore();
    }

    // 여러 설비의 from 이후 점수를 시간 순으로 한 번에 (묶음당 1회)
    @Query(value = """
        SELECT s.equip_id AS equipId, s.predicted_at AS predictedAt, s.score AS score
          FROM pm_score s
         WHERE s.equip_id IN (:ids) AND s.predicted_at >= :from
         ORDER BY s.equip_id, s.predicted_at
        """, nativeQuery = true)
    java.util.List<ScorePoint> findSince(@Param("ids") java.util.Collection<Long> ids, @Param("from") LocalDateTime from);

    // 점수 저장. 같은 설비·같은 시각이 이미 있으면 건너뛴다 (uq_pm_score_equip_time).
    // 예지보전 서버를 다시 켜면 지난 7일치를 또 보내기 때문. 저장했으면 1, 건너뛰었으면 0
    @Modifying
    @Query(value = """
        INSERT INTO pm_score (equip_id, score, predicted_at, level, reasons, detail, model_version)
        VALUES (:equipId, :score, :predictedAt, CAST(:level AS pm_level), CAST(:reasons AS jsonb),
                CAST(:detail AS jsonb), :modelVersion)
        ON CONFLICT (equip_id, predicted_at) DO NOTHING
        """, nativeQuery = true)
    int insertIgnoreDuplicate(@Param("equipId") Long equipId, @Param("score") BigDecimal score,
                              @Param("predictedAt") LocalDateTime predictedAt, @Param("level") String level,
                              @Param("reasons") String reasons, @Param("detail") String detail,
                              @Param("modelVersion") String modelVersion);
}
