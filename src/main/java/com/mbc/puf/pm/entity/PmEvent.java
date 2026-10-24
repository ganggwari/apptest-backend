package com.mbc.puf.pm.entity;

import com.mbc.puf.common.enums.PmEventStatus;
import com.mbc.puf.common.enums.PmLevel;
import com.mbc.puf.common.enums.PmTriggerType;
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
import org.hibernate.generator.EventType;

/**
 * 예지보전 이벤트 (테이블: pm_event). 점검 목록 역할.
 * 만들어지는 경우(trigger_type): 위험진입(점수가 위험 임계치를 넘어섬) / 급상승(주의 이상이면서 24시간 최저보다 크게 오름) /
 * 미수신(센서 데이터가 끊김, 점수·단계 없음). 주의 단계에 머무르는 것만으로는 만들지 않는다.
 * 점수 기반 이벤트는 처리 중(확인필요·점검중)인 동안 같은 설비에 새로 만들지 않고, 미수신 이벤트는 따로 설비당 1건만 둔다.
 */
@Entity
@Table(name = "pm_event")
public class PmEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "equip_id")
    private Long equipId;

    @Column(name = "trigger_type")
    private PmTriggerType triggerType;   // 만들어진 이유: 위험진입 / 급상승 / 미수신

    @Column(name = "level")
    private PmLevel level;           // 넘은 임계치 단계 (주의 → 위험으로 오르면 여기만 올린다, 미수신이면 null)

    @Column(name = "score")
    private BigDecimal score;        // 이벤트 발생(또는 단계 상승) 시점 점수

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PmEventStatus status = PmEventStatus.CHECK_NEEDED;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public PmEvent() {
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

    public PmTriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(PmTriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public PmLevel getLevel() {
        return level;
    }

    public void setLevel(PmLevel level) {
        this.level = level;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public PmEventStatus getStatus() {
        return status;
    }

    public void setStatus(PmEventStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
