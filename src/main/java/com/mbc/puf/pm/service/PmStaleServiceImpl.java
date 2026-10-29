package com.mbc.puf.pm.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.ManagerNotificationCategory;
import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.enums.PmEventStatus;
import com.mbc.puf.common.enums.PmTriggerType;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.notification.service.NotificationService;
import com.mbc.puf.pm.dto.PmStreamPayloads;
import com.mbc.puf.pm.entity.PmEvent;
import com.mbc.puf.pm.repository.PmEventRepository;
import com.mbc.puf.stream.StreamPublisher;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 센서 미수신 감시 구현.
 *  - 끊김 기준 = max(센서 주기 × puf.pm.stale.multiplier(기본 3), puf.pm.stale.min-seconds(기본 60)) 초
 *    → 펌프 1분 / 수위 15분 / CCTV·전광판 3시간
 *  - 한 번 끊긴 동안(마지막 측정 이후)에는 이벤트를 1건만 만든다. 관리자가 끝내도 같은 끊김으로는 다시 만들지 않는다.
 *    데이터가 다시 들어온 뒤 또 끊기면 새로 만든다.
 *  - 이벤트는 자동으로 끝내지 않는다. 관리자가 조치 내용을 적고 조치완료/기타로 끝낸다 (PmEventService.changeStatus).
 */
@Service
public class PmStaleServiceImpl implements PmStaleService {

    private static final String FRESHNESS_SQL = """
            SELECT q.equip_id, q.name, q.type, q.interval_sec, q.underpass_id, q.underpass_name, q.region_id, q.last_at,
                   EXISTS (SELECT 1 FROM pm_event ev WHERE ev.equip_id = q.equip_id
                              AND CAST(ev.trigger_type AS text) = '미수신' AND ev.created_at > q.last_at) AS handled
              FROM (
                SELECT e.id AS equip_id, e.name AS name, CAST(e.type AS text) AS type, e.sensor_interval_sec AS interval_sec,
                       u.id AS underpass_id, u.name AS underpass_name, u.region_id AS region_id,
                       CASE CAST(e.type AS text)
                         WHEN 'PUMP' THEN (SELECT MAX(s.recorded_at) FROM pm_sensor_pump s WHERE s.equip_id = e.id)
                         WHEN 'CCTV' THEN (SELECT MAX(s.recorded_at) FROM pm_sensor_cctv s WHERE s.equip_id = e.id)
                         WHEN 'DISPLAY' THEN (SELECT MAX(s.recorded_at) FROM pm_sensor_display s WHERE s.equip_id = e.id)
                         WHEN 'WATER_LEVEL' THEN (SELECT MAX(s.recorded_at) FROM pm_sensor_water_level s WHERE s.equip_id = e.id)
                       END AS last_at
                  FROM pm_equipment e
                  JOIN underpass u ON u.id = e.underpass_id AND u.is_deleted = FALSE
                 WHERE e.is_deleted = FALSE AND e.data_started_at IS NOT NULL AND CAST(e.type AS text) <> 'ROAD'
                   %s
              ) q
             WHERE q.last_at IS NOT NULL
             ORDER BY q.equip_id
            """;

    @PersistenceContext
    private EntityManager em;

    private final PmEventRepository eventRepository;
    private final NotificationService notificationService;
    private final StreamPublisher streamPublisher;
    private final DbClock dbClock;
    private final int multiplier;
    private final int minSeconds;

    public PmStaleServiceImpl(PmEventRepository eventRepository, NotificationService notificationService,
            StreamPublisher streamPublisher, DbClock dbClock,
            @Value("${puf.pm.stale.multiplier:3}") int multiplier,
            @Value("${puf.pm.stale.min-seconds:60}") int minSeconds) {
        this.eventRepository = eventRepository;
        this.notificationService = notificationService;
        this.streamPublisher = streamPublisher;
        this.dbClock = dbClock;
        this.multiplier = Math.max(1, multiplier);
        this.minSeconds = Math.max(1, minSeconds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Freshness> freshness(List<Long> regionIds) {
        boolean scoped = regionIds != null;
        if (scoped && regionIds.isEmpty()) {
            return List.of();
        }
        var query = em.createNativeQuery(FRESHNESS_SQL.formatted(scoped ? "AND u.region_id IN (:regionIds)" : ""));
        if (scoped) {
            query.setParameter("regionIds", regionIds);
        }
        LocalDateTime now = dbClock.now();
        List<Freshness> result = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] r = (Object[]) row;
            int interval = ((Number) r[3]).intValue();
            int threshold = (int) Math.min(Integer.MAX_VALUE, Math.max((long) interval * multiplier, minSeconds));
            LocalDateTime lastAt = toLocalDateTime(r[7]);
            boolean stale = lastAt.plusSeconds(threshold).isBefore(now);
            result.add(new Freshness(((Number) r[0]).longValue(), (String) r[1], PmEquipmentType.valueOf((String) r[2]),
                    ((Number) r[4]).longValue(), (String) r[5], ((Number) r[6]).longValue(), interval, threshold, lastAt,
                    stale, (Boolean) r[8]));
        }
        return result;
    }

    @Override
    @Transactional
    public int raiseNoDataEvents() {
        LocalDateTime now = dbClock.now();
        Map<Long, List<PmStreamPayloads.Item>> byRegion = new LinkedHashMap<>();
        int created = 0;
        for (Freshness f : freshness(null)) {
            if (!f.stale() || f.handled()) {
                continue;
            }
            PmEvent ev = new PmEvent();
            ev.setEquipId(f.equipId());
            ev.setTriggerType(PmTriggerType.NO_DATA);
            ev.setStatus(PmEventStatus.CHECK_NEEDED);   // 점수·단계는 없음
            eventRepository.saveAndFlush(ev);
            created++;
            notificationService.notifyManagers(ManagerNotificationCategory.EQUIPMENT, f.regionId(), "설비 데이터 미수신",
                    String.format("%s · %s %s · 센서 데이터가 %s째 수신되지 않았습니다 (마지막 %s)", f.underpassName(), typeKo(f.type()), f.name(),
                            span(Duration.between(f.lastAt(), now)), f.lastAt().toString().replace('T', ' ')),
                    "PM_EVENT", ev.getId());
            streamPublisher.dataChangedToAdmins(f.regionId(), "pm-event", "CREATE", ev.getId());
            byRegion.computeIfAbsent(f.regionId(), k -> new ArrayList<>())
                    .add(new PmStreamPayloads.Item(f.equipId(), f.name(), f.underpassId(), f.type(), null, null, true));
        }
        for (Map.Entry<Long, List<PmStreamPayloads.Item>> e : byRegion.entrySet()) {
            streamPublisher.publishToAdmins(e.getKey(), "pm-update", new PmStreamPayloads.PmUpdate(e.getKey(), now, e.getValue()));
        }
        return created;
    }

    /** 네이티브 쿼리의 시각 값 (드라이버·하이버네이트 버전에 따라 LocalDateTime 또는 Timestamp) */
    static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) value;
    }

    private static String typeKo(PmEquipmentType type) {
        return switch (type) {
            case ROAD -> "노면";
            case CCTV -> "CCTV";
            case DISPLAY -> "전광판";
            case PUMP -> "배수펌프";
            case WATER_LEVEL -> "수위센서";
        };
    }

    private static String span(Duration d) {
        long minutes = Math.max(1, d.toMinutes());
        return minutes >= 60 ? (minutes / 60) + "시간" + (minutes % 60 == 0 ? "" : " " + (minutes % 60) + "분") : minutes + "분";
    }
}
