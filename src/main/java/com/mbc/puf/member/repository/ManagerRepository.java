package com.mbc.puf.member.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.common.enums.ManagerStatus;
import com.mbc.puf.member.entity.Manager;

public interface ManagerRepository extends JpaRepository<Manager, Long> {

    Optional<Manager> findByLoginId(String loginId);

    // 같은 로그인 아이디가 있는지 (탈퇴 계정 포함: DB의 UNIQUE 제약이 탈퇴 행도 대상이므로)
    boolean existsByLoginId(String loginId);

    // 그 직원이 가진 '탈퇴가 아닌' 계정 수 (DB 부분 유니크 인덱스 uq_manager_employee_active 와 같은 조건)
    long countByEmployeeIdAndStatusNot(Long employeeId, ManagerStatus status);

    // 자동 승인(스케줄러): 가입한 지 일정 시간이 지난 승인대기 계정을 한 번에 ACTIVE 로
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Manager m set m.status = com.mbc.puf.common.enums.ManagerStatus.ACTIVE, m.approvedAt = :now"
            + " where m.status = com.mbc.puf.common.enums.ManagerStatus.PENDING and m.createdAt <= :cutoff")
    int approvePendingCreatedBefore(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);

    // 탈퇴 처리. WHERE 에 현재 상태를 걸어서 두 요청이 동시에 와도 한 번만 적용된다 (0 = 이미 처리됨)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Manager m set m.status = com.mbc.puf.common.enums.ManagerStatus.WITHDRAWN"
            + " where m.id = :id and m.status <> com.mbc.puf.common.enums.ManagerStatus.WITHDRAWN")
    int withdraw(@Param("id") Long id);
}
