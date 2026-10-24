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

    // 사용 중인 모든 토큰 (전체 고객 대상 푸시용)
    List<DeviceToken> findByActiveTrue();

    // 만료된 토큰 한 개 사용 중지 (행은 보존)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update DeviceToken t set t.active = false where t.id = :id")
    int deactivateById(@Param("id") Long id);

    // 고객 탈퇴: 그 고객 토큰 전부 사용 중지 (행은 보존)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DeviceToken t set t.active = false where t.customerId = :customerId and t.active = true")
    int deactivateAllByCustomerId(@Param("customerId") Long customerId);
}
