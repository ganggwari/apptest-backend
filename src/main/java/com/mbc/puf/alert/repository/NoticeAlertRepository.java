package com.mbc.puf.alert.repository;

import com.mbc.puf.alert.entity.NoticeAlert;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeAlertRepository extends JpaRepository<NoticeAlert, Long> {

    List<NoticeAlert> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<NoticeAlert> findByIdAndCustomerId(Long id, Long customerId);

    /** 미읽은 공지 알림 건수 */
    long countByCustomerIdAndReadFalse(Long customerId);

    /** 공지 등록 시 전체 고객 대상 일괄 삽입 */
    @Modifying
    @Query(value = """
        INSERT INTO notice_alert (notice_id, customer_id)
        SELECT :noticeId, c.id
        FROM customer c
        WHERE c.is_withdrawn = false
        """, nativeQuery = true)
    int insertForAllActiveCustomers(@Param("noticeId") Long noticeId);
}
