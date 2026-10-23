package com.mbc.puf.alert.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mbc.puf.alert.entity.Alert;

public interface AlertRepository extends JpaRepository<Alert, Long> {
}
