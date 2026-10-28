package com.mbc.puf.pm.repository;

import com.mbc.puf.pm.entity.PmEquipment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PmEquipmentRepository extends JpaRepository<PmEquipment, Long> {

    /** 설비 + 소속 지하차도 이름·지역 (삭제된 설비·지하차도 제외) */
    interface EquipmentRow {
        Long getEquipId();
        String getType();
        String getName();
        Integer getSensorIntervalSec();
        java.time.LocalDateTime getDataStartedAt();
        java.time.LocalDateTime getLastSensorAt();   // 센서 테이블의 마지막 측정 시각 (없으면 null)
        java.time.LocalDateTime getLastScoreAt(); // 자동 점수 백필 중복 계산 방지
        Long getUnderpassId();
        String getUnderpassName();
        Long getRegionId();
    }

    // 예지보전 서버가 받아 갈 전체 설비 목록 (GET /api/internal/pm/equipments)
    @Query(value = """
        SELECT e.id AS equipId, CAST(e.type AS text) AS type, e.name AS name, u.id AS underpassId, u.name AS underpassName,
               u.region_id AS regionId,
               e.sensor_interval_sec AS sensorIntervalSec, e.data_started_at AS dataStartedAt,
               (SELECT MAX(ps.predicted_at) FROM pm_score ps WHERE ps.equip_id = e.id) AS lastScoreAt,
               (CASE CAST(e.type AS text)
                 WHEN 'PUMP' THEN (SELECT MAX(s.recorded_at) FROM pm_sensor_pump s WHERE s.equip_id = e.id)
                 WHEN 'CCTV' THEN (SELECT MAX(s.recorded_at) FROM pm_sensor_cctv s WHERE s.equip_id = e.id)
                 WHEN 'DISPLAY' THEN (SELECT MAX(s.recorded_at) FROM pm_sensor_display s WHERE s.equip_id = e.id)
                 WHEN 'WATER_LEVEL' THEN (SELECT MAX(s.recorded_at) FROM pm_sensor_water_level s WHERE s.equip_id = e.id)
               END) AS lastSensorAt
        FROM pm_equipment e
        JOIN underpass u ON u.id = e.underpass_id
        WHERE u.is_deleted = FALSE AND e.is_deleted = FALSE
        ORDER BY e.id
        """, nativeQuery = true)
    List<EquipmentRow> findAllRows();

    // 점수를 받은 설비들만 (POST /api/internal/pm-scores 처리용)
    @Query(value = """
        SELECT e.id AS equipId, CAST(e.type AS text) AS type, e.name AS name, u.id AS underpassId, u.name AS underpassName,
               u.region_id AS regionId,
               e.sensor_interval_sec AS sensorIntervalSec, e.data_started_at AS dataStartedAt,
               (SELECT MAX(ps.predicted_at) FROM pm_score ps WHERE ps.equip_id = e.id) AS lastScoreAt,
               (CASE CAST(e.type AS text)
                 WHEN 'PUMP' THEN (SELECT MAX(s.recorded_at) FROM pm_sensor_pump s WHERE s.equip_id = e.id)
                 WHEN 'CCTV' THEN (SELECT MAX(s.recorded_at) FROM pm_sensor_cctv s WHERE s.equip_id = e.id)
                 WHEN 'DISPLAY' THEN (SELECT MAX(s.recorded_at) FROM pm_sensor_display s WHERE s.equip_id = e.id)
                 WHEN 'WATER_LEVEL' THEN (SELECT MAX(s.recorded_at) FROM pm_sensor_water_level s WHERE s.equip_id = e.id)
               END) AS lastSensorAt
        FROM pm_equipment e
        JOIN underpass u ON u.id = e.underpass_id
        WHERE u.is_deleted = FALSE AND e.is_deleted = FALSE AND e.id IN (:ids)
        """, nativeQuery = true)
    List<EquipmentRow> findRowsByIds(@Param("ids") Collection<Long> ids);
}
