package com.mbc.puf.display.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.display.entity.DisplayPage;

public interface DisplayPageRepository extends JpaRepository<DisplayPage, Long> {

    /** 특정 전광판의 특정 페이지 번호 */
    Optional<DisplayPage> findByVmsIdAndPageNo(Long vmsId, Integer pageNo);

    /** 특정 전광판의 최대 페이지 번호 (페이지가 없으면 Optional.empty) */
    @Query("select max(dp.pageNo) from DisplayPage dp where dp.vmsId = :vmsId")
    Optional<Integer> findMaxPageNoByVmsId(@Param("vmsId") Long vmsId);

    /** 특정 전광판의 pageNo > deletedPageNo 인 페이지를 모두 1씩 내린다 (삭제 후 재정렬용) */
    @Modifying
    @Query("update DisplayPage dp set dp.pageNo = dp.pageNo - 1 where dp.vmsId = :vmsId and dp.pageNo > :deletedPageNo")
    int shiftPageNoDown(@Param("vmsId") Long vmsId, @Param("deletedPageNo") Integer deletedPageNo);
}
