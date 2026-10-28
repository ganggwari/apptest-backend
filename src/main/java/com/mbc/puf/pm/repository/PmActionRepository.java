package com.mbc.puf.pm.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.pm.entity.PmAction;

public interface PmActionRepository extends JpaRepository<PmAction, Long> {

    /** 설비별 점검 이력 요약 (점수 수신 때 조치 완료 후 쿨다운 판단용) */
    interface MaintenancePoint {
        Long getEquipId();
        /** 가장 최근 '조치 완료' 시각 */
        LocalDateTime getResolvedAt();
        /** 가장 최근 점수 이벤트(위험진입·급상승) 생성 시각. 미수신 이벤트는 뺀다 */
        LocalDateTime getLastEventAt();
    }

    // 여러 설비의 점검 이력 요약을 한 번에 (묶음당 1회). 조치 완료 기록이 없는 설비는 나오지 않는다
    @Query(value = """
        SELECT r.equip_id AS equipId, r.resolved_at AS resolvedAt,
               (SELECT MAX(ev2.created_at) FROM pm_event ev2
                 WHERE ev2.equip_id = r.equip_id AND CAST(ev2.trigger_type AS text) IN ('위험진입', '급상승')) AS lastEventAt
          FROM (SELECT ev.equip_id AS equip_id, MAX(a.created_at) AS resolved_at
                  FROM pm_action a
                  JOIN pm_event ev ON ev.id = a.event_id
                 WHERE ev.equip_id IN (:ids) AND CAST(a.kind AS text) = 'RESOLVED'
                 GROUP BY ev.equip_id) r
        """, nativeQuery = true)
    List<MaintenancePoint> findMaintenance(@Param("ids") Collection<Long> ids);
}
