package com.mbc.puf.threshold.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.threshold.entity.Threshold;

public interface ThresholdRepository extends JpaRepository<Threshold, Long> {

    // 특정 지하차도의 임계치 3행 (하한값 오름차순 = SAFE→WARNING→DANGER)
    List<Threshold> findByUnderpassIdOrderByRiskScoreMinAsc(Long underpassId);

    // 특정 지하차도의 특정 단계 임계치
    Optional<Threshold> findByUnderpassIdAndLevel(Long underpassId, RiskLevel level);
}
