package com.mbc.puf.alert.repository;

import com.mbc.puf.alert.entity.AlertDelivery;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertDeliveryRepository extends JpaRepository<AlertDelivery, Long> {

    List<AlertDelivery> findByAlertId(Long alertId);
}
