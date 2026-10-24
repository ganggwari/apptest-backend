package com.mbc.puf.pm.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 위험 점수 추이 그래프 한 점 (GET /api/pm/equipments/{id}/scores). 구간(bucketMinutes)마다 평균·최고 점수 */
public class PmScorePoint {

    private LocalDateTime at;        // 구간 시작 시각
    private BigDecimal score;        // 구간 평균 점수
    private BigDecimal maxScore;     // 구간 최고 점수

    public PmScorePoint() {
    }

    public LocalDateTime getAt() { return at; }
    public void setAt(LocalDateTime at) { this.at = at; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public BigDecimal getMaxScore() { return maxScore; }
    public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }
}
