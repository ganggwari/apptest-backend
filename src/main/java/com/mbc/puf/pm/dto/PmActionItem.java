package com.mbc.puf.pm.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.enums.PmLevel;

/** 점검 이력(예지보전 조치 기록) 1행 (GET /api/pm/actions, 설비 상세의 최근 점검 이력) */
public class PmActionItem {

    private Long actionId;
    private Long eventId;
    private Long equipId;
    private String name;                // 설비 이름 (예: P-12-1)
    private PmEquipmentType type;
    private Long underpassId;
    private String underpassName;
    private Long regionId;
    private String content;              // 조치 내용
    private Long managerId;
    private String managerName;          // 등록자 (직원 이름)
    private LocalDateTime createdAt;     // 조치 기록 시각
    private PmLevel eventLevel;          // 그 이벤트의 단계
    private BigDecimal scoreBefore;      // 점검 전 점수 (이벤트 점수)
    private BigDecimal scoreAfter;       // 점검 후 점수 (조치 기록 뒤 첫 점수, 아직 없으면 null)

    public PmActionItem() {
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /** name 과 같은 값 (프런트가 code 를 쓰던 때와 호환) */
    public String getCode() { return name; }

    public Long getActionId() { return actionId; }
    public void setActionId(Long actionId) { this.actionId = actionId; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

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

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }

    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public PmLevel getEventLevel() { return eventLevel; }
    public void setEventLevel(PmLevel eventLevel) { this.eventLevel = eventLevel; }

    public BigDecimal getScoreBefore() { return scoreBefore; }
    public void setScoreBefore(BigDecimal scoreBefore) { this.scoreBefore = scoreBefore; }

    public BigDecimal getScoreAfter() { return scoreAfter; }
    public void setScoreAfter(BigDecimal scoreAfter) { this.scoreAfter = scoreAfter; }
}
