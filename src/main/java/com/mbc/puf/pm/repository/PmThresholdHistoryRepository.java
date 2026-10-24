package com.mbc.puf.pm.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mbc.puf.pm.entity.PmThresholdHistory;

public interface PmThresholdHistoryRepository extends JpaRepository<PmThresholdHistory, Long> {
}
