package com.mbc.puf.notification.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.notification.entity.CustomerNotification;

public interface CustomerNotificationRepository extends JpaRepository<CustomerNotification, Long> {

    Optional<CustomerNotification> findByIdAndCustomerId(Long id, Long customerId);

    /** 공지 등록 시 활성 고객 전체에게 공지 알림 일괄 삽입 */
    @Modifying
    @Query(value = """
            INSERT INTO customer_notification (customer_id, category, title, body, ref_type, ref_id)
            SELECT c.id, 'NOTICE', :title, :body, 'NOTICE', :noticeId
            FROM customer c
            WHERE c.is_withdrawn = false
            """, nativeQuery = true)
    int insertNoticeForAllActiveCustomers(@Param("noticeId") Long noticeId, @Param("title") String title,
                                          @Param("body") String body);

    /** 안 읽은 알림 전부 읽음 처리 (분류 지정 없이) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CustomerNotification n set n.read = true where n.customerId = :customerId and n.read = false")
    int markAllRead(@Param("customerId") Long customerId);

    /** 안 읽은 알림 중 한 분류만 읽음 처리 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CustomerNotification n set n.read = true where n.customerId = :customerId and n.read = false"
            + " and n.category = :category")
    int markAllReadByCategory(@Param("customerId") Long customerId,
                              @Param("category") com.mbc.puf.common.enums.CustomerNotificationCategory category);
}
