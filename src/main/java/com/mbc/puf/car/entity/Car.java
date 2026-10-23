package com.mbc.puf.car.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;

/** 고객 등록 차량 (테이블: car) */
@Entity
@Table(name = "car")
public class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "car_num")
    private String carNum;

    @Column(name = "is_deleted")
    private boolean deleted = false;   // 소프트 삭제(본인 삭제·고객 탈퇴). 조회와 알림 매칭에서 항상 빠진다

    @Generated   // DB 기본값(NOW())으로 INSERT 시 채워지고, 저장 직후 값을 다시 읽어온다
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Car() {
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCarNum() {
        return carNum;
    }

    public void setCarNum(String carNum) {
        this.carNum = carNum;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}