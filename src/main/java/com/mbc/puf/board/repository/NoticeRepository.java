package com.mbc.puf.board.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.board.entity.Notice;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Optional<Notice> findByIdAndDeletedFalse(Long id);

    // 조회수 +1 (덮어쓰기를 막으려고 읽고 쓰지 않고 DB에서 바로 더한다)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update notice set view_count = view_count + 1 where id = :id", nativeQuery = true)
    int increaseViewCount(@Param("id") Long id);
}