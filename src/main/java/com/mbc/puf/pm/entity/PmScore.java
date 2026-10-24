package com.mbc.puf.pm.entity;

import com.mbc.puf.common.enums.PmLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnTransformer;

/**
 * 예지보전 위험도 점수 (테이블: pm_score). 예지보전 서버(FastAPI 8002) 계산 결과.
 * 저장은 PmScoreRepository.insertIgnoreDuplicate(같은 설비·같은 시각이면 건너뜀)로 하고, 이 엔티티는 주로 조회용.
 * reasons·detail 은 DB 에서 JSONB, 자바에서는 JSON 문자열 그대로 들고 있다.
 */
@Entity
@Table(name = "pm_score")
public class PmScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "equip_id")
    private Long equipId;

    @Column(name = "score")
    private BigDecimal score;              // 고장 위험도 점수(0~100)

    @Column(name = "predicted_at")
    private LocalDateTime predictedAt;

    @Column(name = "level")
    private PmLevel level;                 // Spring 이 pm_threshold 로 판정한 단계

    @ColumnTransformer(write = "CAST(? AS jsonb)")
    @Column(name = "reasons")
    private String reasons;                // 사유 문장 목록 JSON (예: ["영상 수신률 평소보다 매우 많이 낮아요"])

    @ColumnTransformer(write = "CAST(? AS jsonb)")
    @Column(name = "detail")
    private String detail;                 // 모델별 세부값 JSON

    @Column(name = "model_version")
    private String modelVersion;

    public PmScore() {
    }

    public Long getId() {
        return id;
    }

    public Long getEquipId() {
        return equipId;
    }

    public void setEquipId(Long equipId) {
        this.equipId = equipId;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public LocalDateTime getPredictedAt() {
        return predictedAt;
    }

    public void setPredictedAt(LocalDateTime predictedAt) {
        this.predictedAt = predictedAt;
    }

    public PmLevel getLevel() {
        return level;
    }

    public void setLevel(PmLevel level) {
        this.level = level;
    }

    public String getReasons() {
        return reasons;
    }

    public void setReasons(String reasons) {
        this.reasons = reasons;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }
}
