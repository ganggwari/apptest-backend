package com.mbc.puf.notification.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Generated;

import com.mbc.puf.common.enums.CustomerNotificationCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 고객 알림 센터의 알림 1건 (테이블: customer_notification). 위험/공지/문의를 한 곳에 모은다 */
@Entity
@Table(name = "customer_notification")
public class CustomerNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "customer_id")
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private CustomerNotificationCategory category;

    @Column(name = "title")
    private String title;

    @Column(name = "body")
    private String body;

    @Column(name = "ref_type")
    private String refType;   // ALERT / NOTICE / INQUIRY (눌렀을 때 이동할 대상 종류)

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "is_read")
    private boolean read = false;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public CustomerNotification() {
    }

    public Long getId() { return id; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public CustomerNotificationCategory getCategory() { return category; }
    public void setCategory(CustomerNotificationCategory category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }

    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
