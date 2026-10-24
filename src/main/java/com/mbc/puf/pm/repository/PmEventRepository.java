package com.mbc.puf.pm.repository;

import com.mbc.puf.common.enums.PmEventStatus;
import com.mbc.puf.pm.entity.PmEvent;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PmEventRepository extends JpaRepository<PmEvent, Long> {

    // 여러 설비의 처리 중(확인필요·점검중) 이벤트 한 번에. 있으면 새 이벤트를 만들지 않는다
    List<PmEvent> findByEquipIdInAndStatusIn(Collection<Long> equipIds, Collection<PmEventStatus> statuses);
}
