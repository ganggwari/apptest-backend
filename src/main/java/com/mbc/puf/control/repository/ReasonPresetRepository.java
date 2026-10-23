package com.mbc.puf.control.repository;

import java.util.List;

import com.mbc.puf.common.enums.ReasonType;
import com.mbc.puf.control.entity.ReasonPreset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReasonPresetRepository extends JpaRepository<ReasonPreset, Long> {

    List<ReasonPreset> findByTypeOrderByIdAsc(ReasonType type);

    List<ReasonPreset> findAllByOrderByIdAsc();
}