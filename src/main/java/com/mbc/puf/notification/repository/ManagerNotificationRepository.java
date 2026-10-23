package com.mbc.puf.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mbc.puf.notification.entity.ManagerNotification;

public interface ManagerNotificationRepository extends JpaRepository<ManagerNotification, Long> {
}
