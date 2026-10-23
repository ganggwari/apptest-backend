package com.mbc.puf.member.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mbc.puf.member.entity.Shift;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    List<Shift> findAllByOrderByStartTimeAsc();
}
