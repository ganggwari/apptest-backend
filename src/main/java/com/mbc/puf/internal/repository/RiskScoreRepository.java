package com.mbc.puf.internal.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mbc.puf.internal.entity.RiskScore;

public interface RiskScoreRepository extends JpaRepository<RiskScore, Long> {

    // 최근 예측 N개 (최신 먼저)
    List<RiskScore> findByUnderpassIdOrderByPredictedAtDesc(Long underpassId, Limit limit);

    // "최근 N시간" 범위 안에서 최근 예측 N개 (최신 먼저)
    List<RiskScore> findByUnderpassIdAndPredictedAtGreaterThanEqualOrderByPredictedAtDesc(Long underpassId,
                                                                                         LocalDateTime since,
                                                                                         Limit limit);

    // 어떤 시각 이전(포함)에 나온 최근 예측 N개 (최신 먼저). 통제 전달서에 이벤트 시점 점수를 적을 때 쓴다
    List<RiskScore> findByUnderpassIdAndPredictedAtLessThanEqualOrderByPredictedAtDesc(Long underpassId,
                                                                                      LocalDateTime at,
                                                                                      Limit limit);
}