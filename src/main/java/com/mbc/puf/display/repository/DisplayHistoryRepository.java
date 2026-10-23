package com.mbc.puf.display.repository;

import com.mbc.puf.display.entity.DisplayHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisplayHistoryRepository extends JpaRepository<DisplayHistory, Long> {
}
