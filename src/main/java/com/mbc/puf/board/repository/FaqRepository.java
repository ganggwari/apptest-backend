package com.mbc.puf.board.repository;

import java.util.Optional;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.board.entity.Faq;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    Optional<Faq> findByIdAndDeletedFalse(Long id);

    List<Faq> findByIdInAndDeletedFalse(List<Long> ids);

    // 삭제되지 않은 FAQ 중 가장 큰 순서 번호 (없으면 0)
    @Query("select coalesce(max(f.sortOrder), 0) from Faq f where f.deleted = false")
    int findMaxSortOrder();

    // 조회수 +1 (덮어쓰기를 막으려고 읽고 쓰지 않고 DB에서 바로 더한다)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update faq set view_count = view_count + 1 where id = :id", nativeQuery = true)
    int increaseViewCount(@Param("id") Long id);
}