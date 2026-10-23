package com.mbc.puf.underpass.entity;

import com.mbc.puf.common.enums.TransitDirection;
import com.mbc.puf.common.enums.TransitStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 지하차도 입출구 통과(잔류차량 추적) (테이블: transit) */
@Entity
@Table(name = "transit")
public class Transit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "underpass_id")
    private Long underpassId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction")
    private TransitDirection direction;   // 상행(UP)/하행(DOWN). 입구 카메라 위치로 정한다

    @Column(name = "car_num")
    private String carNum;

    @Column(name = "entry_log_id")
    private Long entryPlateLogId;

    @Column(name = "exit_log_id")
    private Long exitPlateLogId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TransitStatus status = TransitStatus.IN_TRANSIT;

    @Column(name = "entry_at")
    private LocalDateTime entryAt;

    @Column(name = "exit_at")
    private LocalDateTime exitAt;

    public Transit() {
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

    public String getCarNum() {
        return carNum;
    }

    public void setCarNum(String carNum) {
        this.carNum = carNum;
    }

    public Long getEntryPlateLogId() {
        return entryPlateLogId;
    }

    public void setEntryPlateLogId(Long entryPlateLogId) {
        this.entryPlateLogId = entryPlateLogId;
    }

    public Long getExitPlateLogId() {
        return exitPlateLogId;
    }

    public void setExitPlateLogId(Long exitPlateLogId) {
        this.exitPlateLogId = exitPlateLogId;
    }

    public TransitStatus getStatus() {
        return status;
    }

    public void setStatus(TransitStatus status) {
        this.status = status;
    }

    public LocalDateTime getEntryAt() {
        return entryAt;
    }

    public void setEntryAt(LocalDateTime entryAt) {
        this.entryAt = entryAt;
    }

    public LocalDateTime getExitAt() {
        return exitAt;
    }

    public void setExitAt(LocalDateTime exitAt) {
        this.exitAt = exitAt;
    }

    public TransitDirection getDirection() {
        return direction;
    }

    public void setDirection(TransitDirection direction) {
        this.direction = direction;
    }
}
