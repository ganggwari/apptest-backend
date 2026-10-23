package com.mbc.puf.underpass.entity;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.enums.UnderpassEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;

/** 지하차도 이벤트(위험도상승/하강/차단/해제) (테이블: event) */
@Entity
@Table(name = "event")
public class UnderpassEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "underpass_id")
    private Long underpassId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private UnderpassEventType eventType;

    @Column(name = "risk_level")
    private RiskLevel riskLevel;

    @Column(name = "score")
    private BigDecimal riskScoreAtEvent;

    @Column(name = "water_cm")
    private BigDecimal waterCm;   // 이벤트 시점 수위(cm). 수위 때문에 단계가 바뀌었거나 자동 차단이면 값 있음

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "is_auto")
    private boolean auto = false;   // 시스템 자동 처리(수위 기준 자동 차단)이면 true, operatorId 는 NULL

    @Column(name = "reason")
    private String reason;   // 차단/해제 사유 (프리셋 문구 또는 직접 입력, 위험도 이벤트는 NULL)

    @Column(name = "reason_note")
    private String reasonNote;   // 사유에 덧붙인 상세 사유 (관리자가 따로 적은 글, 없으면 NULL)

    @Generated   // DB 기본값(NOW())으로 INSERT 시 채워지고, 저장 직후 값을 다시 읽어온다
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public UnderpassEvent() {
    }

    public Long getId() {
        return id;
    }

    public Long getUnderpassId() {
        return underpassId;
    }

    public void setUnderpassId(Long underpassId) {
        this.underpassId = underpassId;
    }

    public UnderpassEventType getEventType() {
        return eventType;
    }

    public void setEventType(UnderpassEventType eventType) {
        this.eventType = eventType;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public BigDecimal getRiskScoreAtEvent() {
        return riskScoreAtEvent;
    }

    public void setRiskScoreAtEvent(BigDecimal riskScoreAtEvent) {
        this.riskScoreAtEvent = riskScoreAtEvent;
    }

    public BigDecimal getWaterCm() {
        return waterCm;
    }

    public void setWaterCm(BigDecimal waterCm) {
        this.waterCm = waterCm;
    }

    public boolean isAuto() {
        return auto;
    }

    public void setAuto(boolean auto) {
        this.auto = auto;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReasonNote() {
        return reasonNote;
    }

    public void setReasonNote(String reasonNote) {
        this.reasonNote = reasonNote;
    }
}