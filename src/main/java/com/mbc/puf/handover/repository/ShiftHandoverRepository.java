package com.mbc.puf.handover.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mbc.puf.handover.entity.ShiftHandover;

public interface ShiftHandoverRepository extends JpaRepository<ShiftHandover, Long> {

    // 어느 근무조의 어느 날짜 리포트 중 가장 늦게 쓴 것 (같은 근무조에서 여러 명이 썼을 수 있음)
    Optional<ShiftHandover> findFirstByShiftIdAndShiftDateOrderByIdDesc(Long shiftId, LocalDate shiftDate);

    // 근무조 상관없이 가장 최근 리포트 (근무조가 없는 관리자용)
    Optional<ShiftHandover> findFirstByOrderByIdDesc();
}
