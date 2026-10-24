package com.mbc.puf.pm.repository;

import com.mbc.puf.pm.entity.PmThreshold;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PmThresholdRepository extends JpaRepository<PmThreshold, Long> {

    // 여러 설비의 임계치 한 번에 (점수 묶음을 판정할 때)
    List<PmThreshold> findByEquipIdIn(Collection<Long> equipIds);

    // 한 설비의 임계치 3행 (하한값 오름차순 = 안전 → 주의 → 위험)
    List<PmThreshold> findByEquipIdOrderByMinScoreAsc(Long equipId);
}
