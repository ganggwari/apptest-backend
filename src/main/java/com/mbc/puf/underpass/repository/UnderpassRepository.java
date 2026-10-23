package com.mbc.puf.underpass.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.underpass.entity.Underpass;

public interface UnderpassRepository extends JpaRepository<Underpass, Long> {

    // 삭제되지 않은 지하차도 단건
    Optional<Underpass> findByIdAndDeletedFalse(Long id);

    // 지역 삭제 시 소속 지하차도를 다른 지역(미배정)으로 이동 (이미 삭제된 지하차도는 원래 지역 기록을 남겨둠)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Underpass u set u.regionId = :toRegionId where u.regionId = :fromRegionId and u.deleted = false")
    int moveRegion(@Param("fromRegionId") Long fromRegionId, @Param("toRegionId") Long toRegionId);
}
