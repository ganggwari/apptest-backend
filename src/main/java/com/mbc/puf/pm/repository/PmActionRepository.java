package com.mbc.puf.pm.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mbc.puf.pm.entity.PmAction;

public interface PmActionRepository extends JpaRepository<PmAction, Long> {
}
