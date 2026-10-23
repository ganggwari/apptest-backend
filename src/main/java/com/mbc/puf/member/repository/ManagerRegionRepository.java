package com.mbc.puf.member.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.member.entity.ManagerRegion;

public interface ManagerRegionRepository extends JpaRepository<ManagerRegion, ManagerRegion.Key> {

    // 그 관리자의 담당 지역 id 목록 (지역 id 순)
    @Query("select mr.regionId from ManagerRegion mr where mr.managerId = :managerId order by mr.regionId")
    List<Long> findRegionIds(@Param("managerId") Long managerId);

    // 여러 관리자의 담당 지역을 한 번에 (관리자 목록 화면용)
    List<ManagerRegion> findByManagerIdIn(Collection<Long> managerIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ManagerRegion mr where mr.managerId = :managerId")
    int deleteByManagerId(@Param("managerId") Long managerId);

    // 지역 삭제 시: 그 지역 담당자들을 '미배정'(toRegionId)으로 옮긴다. 이미 '미배정'을 가진 관리자는 건너뛴다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO manager_region (manager_id, region_id)
            SELECT manager_id, :toRegionId FROM manager_region WHERE region_id = :fromRegionId
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int copyRegion(@Param("fromRegionId") Long fromRegionId, @Param("toRegionId") Long toRegionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ManagerRegion mr where mr.regionId = :regionId")
    int deleteByRegionId(@Param("regionId") Long regionId);
}
