package com.mbc.puf.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.member.entity.DeviceToken;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    // 토큰 값은 기기마다 유일하다 (token UNIQUE)
    Optional<DeviceToken> findByToken(String token);

    // 그 고객의 사용 중인 토큰 (알림 발송 대상)
    List<DeviceToken> findByCustomerIdAndActiveTrueOrderByIdAsc(Long customerId);

    // 전체 공지 푸시 대상: 사용 중인 모든 토큰
    List<DeviceToken> findAllByActiveTrue();

    // FCM 이 "없는 토큰"이라고 알려 준 토큰 사용 중지 (행은 보존)
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DeviceToken t set t.active = false where t.token = :token and t.active = true")
    int deactivateByToken(@Param("token") String token);

    // 고객 탈퇴: 그 고객 토큰 전부 사용 중지 (행은 보존)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DeviceToken t set t.active = false where t.customerId = :customerId and t.active = true")
    int deactivateAllByCustomerId(@Param("customerId") Long customerId);
}
