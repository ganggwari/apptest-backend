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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** 설비별 예지보전 임계치 (테이블: pm_threshold). 설비당 안전/주의/위험 3행, 점수가 min_score 이상이면 그 단계 */
@Entity
@Table(name = "pm_threshold")
public class PmThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "equip_id")
    private Long equipId;

    @Column(name = "level")
    private PmLevel level;

    @Column(name = "min_score")
    private BigDecimal minScore;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Generated(event = EventType.INSERT)   // INSERT 땐 DB 기본값(NOW()), 수정 시엔 서비스가 DbClock 시각을 넣는다
    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    public PmThreshold() {
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

    public PmLevel getLevel() {
        return level;
    }

    public void setLevel(PmLevel level) {
        this.level = level;
    }

    public BigDecimal getMinScore() {
        return minScore;
    }

    public void setMinScore(BigDecimal minScore) {
        this.minScore = minScore;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
