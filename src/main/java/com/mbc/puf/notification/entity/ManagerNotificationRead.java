package com.mbc.puf.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 관리자가 읽은 알림 기록 (테이블: manager_notification_read). 읽은 (알림, 관리자)만 행이 생긴다. 조회 조건용 */
@Entity
@Table(name = "manager_notification_read")
public class ManagerNotificationRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "manager_id")
    private Long managerId;

    public ManagerNotificationRead() {
    }

    public Long getId() { return id; }
    public Long getNotificationId() { return notificationId; }
    public Long getManagerId() { return managerId; }
}
