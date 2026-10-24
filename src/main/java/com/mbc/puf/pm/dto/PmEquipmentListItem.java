package com.mbc.puf.pm.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.enums.PmEventStatus;
import com.mbc.puf.common.enums.PmLevel;

/**
 * 예지보전 설비 목록 1행 (GET /api/pm/equipments). NativeSql 이 컬럼 별칭(snake_case)으로 채운다.
 * 점수·단계·사유는 그 설비의 가장 최근 pm_score.
 */
public class PmEquipmentListItem {

    private Long equipId;
    private String name;                // 설비 이름 (예: P-12-1)
    private PmEquipmentType type;
    private Long underpassId;
    private String underpassName;
    private Long regionId;
    private BigDecimal score;              // 최근 점수 (아직 없으면 null)
    private PmLevel level;                 // 최근 단계
    private BigDecimal scoreWeekStart;     // 최근 7일 안에서 가장 오래된 점수 (7일 변화 계산용)
    private LocalDateTime lastReceivedAt;  // 마지막 수신 (최근 점수 시각)
    private String reasonsJson;            // DB jsonb 를 문자열로 받은 것. 서비스가 reasons 로 바꾼다
    private List<String> reasons;          // 주요 사유
    private Long openEventId;              // 처리 중(확인필요·점검중) 이벤트
    private PmEventStatus openEventStatus; // 점검 상태 (없으면 null = 이상 없음)
    private List<BigDecimal> sparkline;    // 최근 24시간 1시간 평균 점수 (오래된 순, 점수 없는 시간은 건너뜀). 목록에서만 채운다

    public PmEquipmentListItem() {
    }

    // 화면 설비 코드 = 설비 이름
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /** name 과 같은 값 (프런트가 code 를 쓰던 때와 호환) */
    public String getCode() { return name; }

    // 7일 변화 (최근 점수 - 7일 안 가장 오래된 점수)
    public BigDecimal getScoreChange7d() {
        return score == null || scoreWeekStart == null ? null : score.subtract(scoreWeekStart);
    }

    public Long getEquipId() { return equipId; }
    public void setEquipId(Long equipId) { this.equipId = equipId; }

    public PmEquipmentType getType() { return type; }
    public void setType(PmEquipmentType type) { this.type = type; }

    public Long getUnderpassId() { return underpassId; }
    public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }

    public String getUnderpassName() { return underpassName; }
    public void setUnderpassName(String underpassName) { this.underpassName = underpassName; }

    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public PmLevel getLevel() { return level; }
    public void setLevel(PmLevel level) { this.level = level; }

    @JsonIgnore
    public BigDecimal getScoreWeekStart() { return scoreWeekStart; }
    public void setScoreWeekStart(BigDecimal scoreWeekStart) { this.scoreWeekStart = scoreWeekStart; }

    public LocalDateTime getLastReceivedAt() { return lastReceivedAt; }
    public void setLastReceivedAt(LocalDateTime lastReceivedAt) { this.lastReceivedAt = lastReceivedAt; }

    @JsonIgnore
    public String getReasonsJson() { return reasonsJson; }
    public void setReasonsJson(String reasonsJson) { this.reasonsJson = reasonsJson; }

    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }

    public List<BigDecimal> getSparkline() { return sparkline; }
    public void setSparkline(List<BigDecimal> sparkline) { this.sparkline = sparkline; }

    public Long getOpenEventId() { return openEventId; }
    public void setOpenEventId(Long openEventId) { this.openEventId = openEventId; }

    public PmEventStatus getOpenEventStatus() { return openEventStatus; }
    public void setOpenEventStatus(PmEventStatus openEventStatus) { this.openEventStatus = openEventStatus; }
}
