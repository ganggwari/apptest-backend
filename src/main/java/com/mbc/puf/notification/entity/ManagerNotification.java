package com.mbc.puf.notification.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Generated;

import com.mbc.puf.common.enums.ManagerNotificationCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 관리자 알림 센터의 알림 1건 (테이블: manager_notification).
 * 알림은 1건만 만들고, 누가 읽었는지는 manager_notification_read 에 따로 남긴다.
 * region_id 가 있으면 그 지역 담당 관리자만, NULL 이면 모든 관리자가 본다.
 */
@Entity
@Table(name = "manager_notification")
public class ManagerNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private ManagerNotificationCategory category;

    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "title")
    private String title;

    @Column(name = "body")
    private String body;

    @Column(name = "ref_type")
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public ManagerNotification() {
    }

    public Long getId() { return id; }

    public ManagerNotificationCategory getCategory() { return category; }
    public void setCategory(ManagerNotificationCategory category) { this.category = category; }

    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }

    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
