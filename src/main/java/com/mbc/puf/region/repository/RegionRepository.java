package com.mbc.puf.region.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mbc.puf.region.entity.Region;

public interface RegionRepository extends JpaRepository<Region, Long> {

    // 삭제되지 않은 지역 전체 ('미배정' 포함), id 순
    List<Region> findByDeletedFalseOrderByIdAsc();

    // 삭제되지 않은 지역 단건
    Optional<Region> findByIdAndDeletedFalse(Long id);

    // 이름으로 삭제되지 않은 지역 조회 (중복 확인, '미배정' 찾기용)
    Optional<Region> findByNameAndDeletedFalse(String name);
}
