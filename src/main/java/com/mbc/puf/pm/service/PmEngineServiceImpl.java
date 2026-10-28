package com.mbc.puf.pm.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.pm.dto.PmEngineStatusResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 엔진 상태.
 *  status : WAITING(서버를 켠 뒤 아직 점수를 못 받음) / NORMAL / DELAYED / DOWN
 *           마지막으로 점수 묶음을 받은 뒤 puf.pm.engine.delayed-seconds(기본 180) 안이면 NORMAL, puf.pm.engine.down-seconds(기본 600) 안이면 DELAYED, 넘으면 DOWN
 *  설비 수·점수 수·센서 끊김은 내 담당 지역만 센다. 마지막 수신 시각은 엔진 전체 기준.
 */
@Service
public class PmEngineServiceImpl implements PmEngineService {

    @PersistenceContext
    private EntityManager em;

    private final PmEngineMonitor monitor;
    private final PmStaleService staleService;
    private final ScopeResolver scopeResolver;
    private final DbClock dbClock;
    private final int delayedSeconds;
    private final int downSeconds;

    public PmEngineServiceImpl(PmEngineMonitor monitor, PmStaleService staleService, ScopeResolver scopeResolver, DbClock dbClock,
            @Value("${puf.pm.engine.delayed-seconds:180}") int delayedSeconds,
            @Value("${puf.pm.engine.down-seconds:600}") int downSeconds) {
        this.monitor = monitor;
        this.staleService = staleService;
        this.scopeResolver = scopeResolver;
        this.dbClock = dbClock;
        this.delayedSeconds = Math.max(1, delayedSeconds);
        this.downSeconds = Math.max(this.delayedSeconds, downSeconds);
    }

    @Override
    @Transactional(readOnly = true)
    public PmEngineStatusResponse getStatus(LoginUser loginUser) {
        List<Long> regions = scopeResolver.regionsOf(loginUser, null);
        LocalDateTime now = dbClock.now();

        LocalDateTime received = monitor.getLastReceivedAt();
        Long since = received == null ? null : Math.max(0, Duration.between(received, now).toSeconds());
        String status = since == null ? "WAITING" : since <= delayedSeconds ? "NORMAL" : since <= downSeconds ? "DELAYED" : "DOWN";

        Map<PmEquipmentType, int[]> counts = new EnumMap<>(PmEquipmentType.class);       // [total, scored]
        Map<PmEquipmentType, LocalDateTime> latestScore = new EnumMap<>(PmEquipmentType.class);
        Map<PmEquipmentType, int[]> staleCount = new EnumMap<>(PmEquipmentType.class);   // [sensorStale]
        Map<PmEquipmentType, LocalDateTime> latestSensor = new EnumMap<>(PmEquipmentType.class);
        for (PmEquipmentType t : PmEquipmentType.values()) {
            if (t == PmEquipmentType.ROAD) continue;
            counts.put(t, new int[2]);
            staleCount.put(t, new int[1]);
        }
        int openNoData = 0;
        if (!regions.isEmpty()) {
            for (Object row : em.createNativeQuery("""
                    SELECT CAST(e.type AS text) AS type, COUNT(*) AS total, COUNT(ls.predicted_at) AS scored, MAX(ls.predicted_at) AS latest
                      FROM pm_equipment e
                      JOIN underpass u ON u.id = e.underpass_id AND u.is_deleted = FALSE
                      LEFT JOIN LATERAL (SELECT s.predicted_at FROM pm_score s WHERE s.equip_id = e.id
                                          ORDER BY s.predicted_at DESC LIMIT 1) ls ON TRUE
                     WHERE e.is_deleted = FALSE AND e.type::text <> 'ROAD' AND u.region_id IN (:regionIds)
                     GROUP BY e.type
                    """).setParameter("regionIds", regions).getResultList()) {
                Object[] r = (Object[]) row;
                PmEquipmentType t = PmEquipmentType.valueOf((String) r[0]);
                counts.put(t, new int[] { ((Number) r[1]).intValue(), ((Number) r[2]).intValue() });
                if (r[3] != null) {
                    latestScore.put(t, PmStaleServiceImpl.toLocalDateTime(r[3]));
                }
            }
            for (PmStaleService.Freshness f : staleService.freshness(regions)) {
                if (f.stale()) {
                    staleCount.get(f.type())[0]++;
                }
                latestSensor.merge(f.type(), f.lastAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
            openNoData = ((Number) em.createNativeQuery("""
                    SELECT COUNT(*) FROM pm_event ev
                      JOIN pm_equipment e ON e.id = ev.equip_id AND e.is_deleted = FALSE
                      JOIN underpass u ON u.id = e.underpass_id AND u.is_deleted = FALSE
                     WHERE CAST(ev.trigger_type AS text) = '미수신' AND CAST(ev.status AS text) IN ('CHECK_NEEDED', 'IN_PROGRESS')
                       AND u.region_id IN (:regionIds)
                    """).setParameter("regionIds", regions).getSingleResult()).intValue();
        }

        List<PmEngineStatusResponse.TypeStatus> byType = new ArrayList<>();
        int total = 0, scored = 0, stale = 0;
        for (PmEquipmentType t : PmEquipmentType.values()) {
            if (t == PmEquipmentType.ROAD) continue;
            int[] c = counts.get(t);
            total += c[0];
            scored += c[1];
            stale += staleCount.get(t)[0];
            byType.add(new PmEngineStatusResponse.TypeStatus(t, c[0], c[1], staleCount.get(t)[0], latestScore.get(t), latestSensor.get(t)));
        }
        return new PmEngineStatusResponse(status, received, since, monitor.getLastBatchSize(), monitor.isLastBatchBackfill(),
                total, scored, stale, openNoData, byType);
    }
}
