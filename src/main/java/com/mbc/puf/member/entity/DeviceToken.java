package com.mbc.puf.member.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Generated;

import com.mbc.puf.common.enums.DevicePlatform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 고객 푸시 토큰 (테이블: device_token). 고객 1명이 웹/앱 여러 기기를 가질 수 있다. 행은 지우지 않고 is_active 로 끈다 */
@Entity
@Table(name = "device_token")
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "token")
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform")
    private DevicePlatform platform;

    @Column(name = "is_active")
    private boolean active = true;

    @Generated   // DB 기본값(NOW())으로 INSERT 시 채워지고, 저장 직후 값을 다시 읽어온다
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public DeviceToken() {
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public DevicePlatform getPlatform() { return platform; }
    public void setPlatform(DevicePlatform platform) { this.platform = platform; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
