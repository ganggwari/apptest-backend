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

/** 관리자 푸시 토큰 (테이블: manager_device_token). 관리자 1명이 여러 기기를 가질 수 있다. 행은 지우지 않고 is_active 로 끈다 */
@Entity
@Table(name = "manager_device_token")
public class ManagerDeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "manager_id")
    private Long managerId;

    @Column(name = "token")
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform")
    private DevicePlatform platform;

    @Column(name = "is_active")
    private boolean active = true;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public ManagerDeviceToken() {
    }

    public Long getId() { return id; }
    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public DevicePlatform getPlatform() { return platform; }
    public void setPlatform(DevicePlatform platform) { this.platform = platform; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
