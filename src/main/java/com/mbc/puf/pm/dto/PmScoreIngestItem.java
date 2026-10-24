package com.mbc.puf.pm.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.JsonNode;

/** 예지보전 서버가 보내는 점수 1건. 단계(level)는 보내지 않는다 — Spring 이 pm_threshold 로 판정 */
public class PmScoreIngestItem {

    @NotNull(message = "equipId는 필수입니다.")
    private Long equipId;

    // 참고용 (설비 종류는 DB 의 pm_equipment.type 을 쓴다)
    private String type;

    @NotNull(message = "score는 필수입니다.")
    @DecimalMin(value = "0.00", message = "score는 0 이상이어야 합니다.")
    @DecimalMax(value = "100.00", message = "score는 100 이하여야 합니다.")   // pm_score.score DECIMAL(5,2)
    private BigDecimal score;

    @NotNull(message = "predictedAt은 필수입니다.")
    private LocalDateTime predictedAt;

    // 사유 문장 목록 (예: ["영상 수신률 평소보다 매우 많이 낮아요"]). 없으면 빈 목록으로 저장
    private List<String> reasons;

    // 모델별 세부값 (CCTV items / 전광판 suspectedCause·failureProb7d / 펌프 topSignals / 수위 anomalyType)
    private JsonNode detail;

    @Size(max = 30, message = "modelVersion은 30자 이하여야 합니다.")
    private String modelVersion;

    public PmScoreIngestItem() {
    }

    public Long getEquipId() { return equipId; }
    public void setEquipId(Long equipId) { this.equipId = equipId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public LocalDateTime getPredictedAt() { return predictedAt; }
    public void setPredictedAt(LocalDateTime predictedAt) { this.predictedAt = predictedAt; }

    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }

    public JsonNode getDetail() { return detail; }
    public void setDetail(JsonNode detail) { this.detail = detail; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
}
