package com.mbc.puf.alert.entity;

import com.mbc.puf.common.enums.AlertType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;

/** 개인화 알림 발송 이력 (테이블: alert) */
@Entity
@Table(name = "alert")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "car_id")
    private Long carId;

    @Column(name = "car_num")
    private String carNum;

    @Column(name = "camera_id")
    private Long cameraId;   // 이 알림을 만든 번호판을 인식한 카메라 (예전 알림은 NULL)

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private AlertType alertType;

    @Column(name = "is_read")
    private boolean read = false;   // 고객이 알림을 열람했는지

    @Generated   // DB 기본값(NOW())으로 INSERT 시 채워지고, 저장 직후 값을 다시 읽어온다
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime sentAt;

    public Alert() {
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getCarId() {
        return carId;
    }

    public void setCarId(Long carId) {
        this.carId = carId;
    }

    public String getCarNum() {
        return carNum;
    }

    public void setCarNum(String carNum) {
        this.carNum = carNum;
    }

    public Long getCameraId() {
        return cameraId;
    }

    public void setCameraId(Long cameraId) {
        this.cameraId = cameraId;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public void setAlertType(AlertType alertType) {
        this.alertType = alertType;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}
