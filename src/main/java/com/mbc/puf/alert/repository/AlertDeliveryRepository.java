package com.mbc.puf.alert.repository;

import com.mbc.puf.alert.entity.AlertDelivery;
import com.mbc.puf.common.enums.DeliveryStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AlertDeliveryRepository extends JpaRepository<AlertDelivery, Long> {

    List<AlertDelivery> findByAlertId(Long alertId);

    // 푸시 발송이 끝난 뒤 토큰별 결과를 기록한다 (발송은 커밋 후 별도 스레드에서 끝나므로 자체 트랜잭션)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update AlertDelivery d set d.status = :status where d.alertId = :alertId and d.tokenId = :tokenId")
    int updateStatus(@Param("alertId") Long alertId, @Param("tokenId") Long tokenId, @Param("status") DeliveryStatus status);
}
