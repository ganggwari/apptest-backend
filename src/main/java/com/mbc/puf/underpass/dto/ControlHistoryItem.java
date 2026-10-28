package com.mbc.puf.underpass.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.enums.UnderpassEventType;

/** 제어이력(MP-16) 한 줄, 그리고 대시보드 통합 타임라인에도 재사용된다.
 *  kind="EVENT"면 eventType/riskLevel/riskScoreAtEvent/reason이, kind="DISPLAY"면 displayContent(바뀐 전광판 문구)가,
 *  kind="ALERT"(대시보드 타임라인 전용)면 나머지가 전부 null인 채 createdAt(발송시각)만 값을 가진다.
 *  operatorId는 관리자 이름 조인 없이 id만 (EVENT는 event.operator_id, DISPLAY는 display_history.changed_by) */
public class ControlHistoryItem {

    private final String kind;   // "EVENT" | "DISPLAY" | "ALERT"
    private final Long underpassId;
    private final String underpassName;
    private final UnderpassEventType eventType;
    private final RiskLevel riskLevel;
    private final BigDecimal riskScoreAtEvent;
    private final String reason;           // 차단/해제 사유 (event.reason)
    private final String displayContent;   // 전광판 변경 후 문구 (display_history.new_content)
    private final Long operatorId;
    private final LocalDateTime createdAt;
    private String operatorName;          // 처리한 직원 이름 (자동 처리면 null)
    private String reasonNote;             // 상세 사유 (event.reason_note, 없으면 null)

    public ControlHistoryItem(String kind, Long underpassId, String underpassName, UnderpassEventType eventType,
                              RiskLevel riskLevel, BigDecimal riskScoreAtEvent, String reason, String displayContent,
                              Long operatorId, LocalDateTime createdAt) {
        this.kind = kind;
        this.underpassId = underpassId;
        this.underpassName = underpassName;
        this.eventType = eventType;
        this.riskLevel = riskLevel;
        this.riskScoreAtEvent = riskScoreAtEvent;
        this.reason = reason;
        this.displayContent = displayContent;
        this.operatorId = operatorId;
        this.createdAt = createdAt;
    }

    public String getKind() { return kind; }
    public Long getUnderpassId() { return underpassId; }
    public String getUnderpassName() { return underpassName; }
    public UnderpassEventType getEventType() { return eventType; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public BigDecimal getRiskScoreAtEvent() { return riskScoreAtEvent; }
    public String getReason() { return reason; }
    public String getDisplayContent() { return displayContent; }
    public Long getOperatorId() { return operatorId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getReasonNote() { return reasonNote; }
    public void setReasonNote(String reasonNote) { this.reasonNote = reasonNote; }
}