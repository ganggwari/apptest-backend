package com.mbc.puf.board.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import com.mbc.puf.board.entity.Inquiry;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    // 삭제되지 않은 원글 (parent_id IS NULL)
    Optional<Inquiry> findByIdAndParentIdIsNullAndDeletedFalse(Long id);

    // 원글에 달린 살아있는 답글 (원글당 1개)
    Optional<Inquiry> findByParentIdAndDeletedFalse(Long parentId);

    // 고객 탈퇴: 그 고객이 쓴 원글 전부 소프트 삭제 (관리자 답글은 그대로, 원글이 지워지니 목록에서 함께 빠진다)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Inquiry i set i.deleted = true"
            + " where i.customerId = :customerId and i.parentId is null and i.deleted = false")
    int softDeleteAllByCustomerId(@Param("customerId") Long customerId);
}