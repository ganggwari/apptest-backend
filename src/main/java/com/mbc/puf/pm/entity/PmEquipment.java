package com.mbc.puf.pm.entity;

import com.mbc.puf.common.enums.PmEquipmentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** 예지보전 설비 (테이블: pm_equipment). 지하차도별 CCTV / 전광판 / 배수펌프 / 수위센서 */
@Entity
@Table(name = "pm_equipment")
public class PmEquipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "underpass_id")
    private Long underpassId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private PmEquipmentType type;

    @Generated(event = EventType.INSERT)
    @Column(name = "name", insertable = false, updatable = false)
    private String name;         // 설비 이름 (예: P-12-1). INSERT 때 DB 트리거가 부여하므로 코드에서 넣지 않는다

    @Column(name = "cam_id")
    private Long camId;          // type=CCTV 일 때만

    @Column(name = "its_vms_id")
    private Long itsVmsId;       // type=DISPLAY 일 때만

    @Column(name = "installed_at")
    private LocalDate installedAt;       // 설치일 (모르면 null)

    @Generated(event = EventType.INSERT)
    @Column(name = "sensor_interval_sec", insertable = false, updatable = false)
    private Integer sensorIntervalSec;   // 센서 측정 주기(초). 종류별 기본값을 DB 트리거가 채움

    @Column(name = "data_started_at")
    private LocalDateTime dataStartedAt; // 센서 데이터가 시작된 시각 (stub/백필 시작 때 기록)

    @Column(name = "is_deleted")
    private boolean deleted;             // 삭제 표시 (true 면 목록·FastAPI 조회에서 제외)

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public PmEquipment() {
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

    public PmEquipmentType getType() {
        return type;
    }

    public void setType(PmEquipmentType type) {
        this.type = type;
    }

    public Long getCamId() {
        return camId;
    }

    public void setCamId(Long camId) {
        this.camId = camId;
    }

    public Long getItsVmsId() {
        return itsVmsId;
    }

    public void setItsVmsId(Long itsVmsId) {
        this.itsVmsId = itsVmsId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getInstalledAt() {
        return installedAt;
    }

    public void setInstalledAt(LocalDate installedAt) {
        this.installedAt = installedAt;
    }

    public Integer getSensorIntervalSec() {
        return sensorIntervalSec;
    }

    public LocalDateTime getDataStartedAt() {
        return dataStartedAt;
    }

    public void setDataStartedAt(LocalDateTime dataStartedAt) {
        this.dataStartedAt = dataStartedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
