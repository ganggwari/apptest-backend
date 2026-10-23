package com.mbc.puf.display.repository;

import com.mbc.puf.common.enums.DisplayCategory;
import com.mbc.puf.display.entity.DisplayPreset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisplayPresetRepository extends JpaRepository<DisplayPreset, Long> {

    List<DisplayPreset> findByCategory(DisplayCategory category);
}
