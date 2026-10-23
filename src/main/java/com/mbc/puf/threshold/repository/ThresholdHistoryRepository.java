package com.mbc.puf.threshold.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mbc.puf.threshold.entity.ThresholdHistory;

public interface ThresholdHistoryRepository extends JpaRepository<ThresholdHistory, Long> {
}
