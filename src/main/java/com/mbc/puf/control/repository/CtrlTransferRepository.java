package com.mbc.puf.control.repository;

import com.mbc.puf.control.entity.CtrlTransfer;
import com.mbc.puf.common.enums.TransferAgency;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CtrlTransferRepository extends JpaRepository<CtrlTransfer, Long> {

    /** 특정 이벤트에 대한 특정 기관 이관 이력 조회 */
    List<CtrlTransfer> findByEventIdAndAgency(Long eventId, TransferAgency agency);

    /** ID로 단건 조회 */
    Optional<CtrlTransfer> findById(Long id);

    /**
     * 특정 지하차도의 이관 기록 전체 (최신순)
     * event 테이블을 경유해 underpass_id 로 필터링
     */
    @Query(value = """
            SELECT ct.*
              FROM ctrl_transfer ct
              JOIN event e ON e.id = ct.event_id
             WHERE e.underpass_id = :underpassId
             ORDER BY ct.created_at DESC, ct.id DESC
            """, nativeQuery = true)
    List<CtrlTransfer> findByUnderpassId(@Param("underpassId") Long underpassId);
}
