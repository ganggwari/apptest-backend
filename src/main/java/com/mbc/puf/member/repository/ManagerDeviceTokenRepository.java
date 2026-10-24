package com.mbc.puf.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.member.entity.ManagerDeviceToken;

public interface ManagerDeviceTokenRepository extends JpaRepository<ManagerDeviceToken, Long> {

    Optional<ManagerDeviceToken> findByToken(String token);

    List<ManagerDeviceToken> findByManagerIdAndActiveTrueOrderByIdAsc(Long managerId);

    // 알림을 받을 관리자들의 사용 중 토큰. regionId 가 null 이면 모든 (활성) 관리자, 아니면 그 지역 담당 관리자만
    @Query(value = """
            SELECT t.* FROM manager_device_token t
            JOIN manager m ON m.id = t.manager_id
            WHERE t.is_active AND m.status = 'ACTIVE'
              AND (CAST(:regionId AS BIGINT) IS NULL
                   OR EXISTS (SELECT 1 FROM manager_region mr WHERE mr.manager_id = m.id AND mr.region_id = :regionId))
            ORDER BY t.id
            """, nativeQuery = true)
    List<ManagerDeviceToken> findActiveForRegion(@Param("regionId") Long regionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update ManagerDeviceToken t set t.active = false where t.id = :id")
    int deactivateById(@Param("id") Long id);
}
