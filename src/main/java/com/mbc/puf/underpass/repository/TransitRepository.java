package com.mbc.puf.underpass.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.common.enums.TransitDirection;
import com.mbc.puf.common.enums.TransitStatus;
import com.mbc.puf.underpass.entity.Transit;

public interface TransitRepository extends JpaRepository<Transit, Long> {

    // 출구 매칭 대상: 같은 지하차도·같은 방향에서 아직 안 나간(IN_TRANSIT/REMAINING) 같은 번호판 중 가장 최근에 들어온 것
    // (오래전에 들어와 출구를 놓친 기록이 남아 있어도, 방금 들어온 기록이 닫히도록)
    Optional<Transit> findFirstByUnderpassIdAndDirectionAndCarNumAndStatusInOrderByEntryAtDesc(
            Long underpassId, TransitDirection direction, String carNum, Collection<TransitStatus> statuses);

    // 출구 보조 매칭: 같은 번호가 없을 때, from~to 사이에 들어온 차 중 숫자가 모두 같고 길이도 같은 것
    // (OCR이 가운데 한글만 다르게 읽은 경우: 337더1901 ↔ 337저1901). 정규식 함수가 필요해서 네이티브 쿼리
    @Query(value = "SELECT * FROM transit"
            + " WHERE underpass_id = :underpassId"
            + "   AND direction = CAST(:direction AS transit_direction)"
            + "   AND status IN ('IN_TRANSIT', 'REMAINING')"
            + "   AND regexp_replace(car_num, '[^0-9]', '', 'g') = :digits"
            + "   AND length(car_num) = length(:carNum)"
            + "   AND entry_at BETWEEN :from AND :to"
            + " ORDER BY entry_at DESC LIMIT 1", nativeQuery = true)
    Optional<Transit> findSimilarActive(@Param("underpassId") Long underpassId, @Param("direction") String direction,
                                        @Param("carNum") String carNum,
                                        @Param("digits") String digits, @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    // BLOCK 시점에 그 지하차도의 IN_TRANSIT 상태 통과 기록을 전부 REMAINING으로 일괄 처리
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Transit t set t.status = com.mbc.puf.common.enums.TransitStatus.REMAINING"
            + " where t.underpassId = :underpassId and t.status = com.mbc.puf.common.enums.TransitStatus.IN_TRANSIT")
    int markRemaining(@Param("underpassId") Long underpassId);

    // PREDICTIVE 알림 대상: 지금 그 지하차도 안에 있는(IN_TRANSIT/REMAINING) 차량번호 (중복제거)
    @Query("select distinct t.carNum from Transit t where t.underpassId = :underpassId"
            + " and t.status in (com.mbc.puf.common.enums.TransitStatus.IN_TRANSIT,"
            + " com.mbc.puf.common.enums.TransitStatus.REMAINING)")
    List<String> findActiveCarNums(@Param("underpassId") Long underpassId);
}
