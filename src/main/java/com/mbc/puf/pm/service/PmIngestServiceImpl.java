package com.mbc.puf.pm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.ManagerNotificationCategory;
import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.enums.PmEventStatus;
import com.mbc.puf.common.enums.PmLevel;
import com.mbc.puf.common.enums.PmTriggerType;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.notification.service.NotificationService;
import com.mbc.puf.pm.dto.PmInternalEquipmentItem;
import com.mbc.puf.pm.dto.PmScoreIngestItem;
import com.mbc.puf.pm.dto.PmScoreIngestRequest;
import com.mbc.puf.pm.dto.PmScoreIngestResponse;
import com.mbc.puf.pm.dto.PmStreamPayloads;
import com.mbc.puf.pm.entity.PmEvent;
import com.mbc.puf.pm.entity.PmThreshold;
import com.mbc.puf.pm.repository.PmEquipmentRepository;
import com.mbc.puf.pm.repository.PmEquipmentRepository.EquipmentRow;
import com.mbc.puf.pm.repository.PmEventRepository;
import com.mbc.puf.pm.repository.PmScoreRepository;
import com.mbc.puf.pm.repository.PmThresholdRepository;
import com.mbc.puf.stream.StreamPublisher;

import tools.jackson.databind.ObjectMapper;

/**
 * 예지보전 점수 수신.
 *
 * 점수 1건마다:
 *   ① 같은 설비·같은 시각이 이미 있으면 건너뜀
 *   ② pm_threshold 로 단계 판정 → pm_score 에 점수·단계·사유·세부값 저장
 *   ③ backfill 이면 여기서 끝 (이벤트·알림·SSE 없음)
 *   ④ 이벤트 트리거를 본다 (주의 단계에 머무는 것만으로는 이벤트를 만들지 않는다):
 *        위험진입 = 직전 점수는 위험 아래였고 지금은 위험
 *        급상승   = 지금 주의 이상이고, 직전 N시간(기본 24) 최저 점수보다 M점(기본 25) 이상 오름
 *      처리 중(확인필요·점검중)인 점수 이벤트가 없으면 새로 만들고 관리자 알림, 있으면 단계가 올라갈 때만 그 이벤트를 올리고 알림.
 *      위험 단계가 되면 긴급 알림(긴급 팝업 + 푸시 긴급).
 * 묶음이 끝나면 지역마다 SSE "pm-update" 를 한 번씩 보낸다.
 *
 * 부하를 줄이려고 묶음당 조회는 4번(설비, 임계치, 처리 중 이벤트, 최근 점수)만 하고, 나머지는 메모리에서 판정한다.
 * 미수신(센서 끊김) 이벤트는 여기서 만들지 않는다 (PmStaleServiceImpl).
 */
@Service
public class PmIngestServiceImpl implements PmIngestService {

    private static final List<PmEventStatus> OPEN_STATUSES = List.of(PmEventStatus.CHECK_NEEDED, PmEventStatus.IN_PROGRESS);
    private static final Map<PmEquipmentType, String> TYPE_KO = Map.of(
            PmEquipmentType.CCTV, "CCTV", PmEquipmentType.DISPLAY, "전광판",
            PmEquipmentType.PUMP, "배수펌프", PmEquipmentType.WATER_LEVEL, "수위센서");
    private static final int BODY_MAX = 250;   // manager_notification.body 는 255자

    private final PmEquipmentRepository equipmentRepository;
    private final PmScoreRepository scoreRepository;
    private final PmThresholdRepository thresholdRepository;
    private final PmEventRepository eventRepository;
    private final NotificationService notificationService;
    private final StreamPublisher streamPublisher;
    private final ObjectMapper objectMapper;
    private final DbClock dbClock;
    private final PmEngineMonitor engineMonitor;
    private final BigDecimal surgeDelta;
    private final int surgeWindowHours;

    public PmIngestServiceImpl(PmEquipmentRepository equipmentRepository, PmScoreRepository scoreRepository,
            PmThresholdRepository thresholdRepository, PmEventRepository eventRepository,
            NotificationService notificationService, StreamPublisher streamPublisher, ObjectMapper objectMapper,
            DbClock dbClock, PmEngineMonitor engineMonitor,
            @Value("${puf.pm.surge-delta:25}") BigDecimal surgeDelta,
            @Value("${puf.pm.surge-window-hours:24}") int surgeWindowHours) {
        this.equipmentRepository = equipmentRepository;
        this.scoreRepository = scoreRepository;
        this.thresholdRepository = thresholdRepository;
        this.eventRepository = eventRepository;
        this.notificationService = notificationService;
        this.streamPublisher = streamPublisher;
        this.objectMapper = objectMapper;
        this.dbClock = dbClock;
        this.engineMonitor = engineMonitor;
        this.surgeDelta = surgeDelta;
        this.surgeWindowHours = Math.max(1, surgeWindowHours);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PmInternalEquipmentItem> getEquipments() {
        return equipmentRepository.findAllRows().stream()
                .map(r -> new PmInternalEquipmentItem(r.getEquipId(), r.getName(), r.getType(), r.getUnderpassId(), r.getUnderpassName(),
                        r.getSensorIntervalSec(), r.getDataStartedAt(), r.getLastSensorAt()))
                .toList();
    }

    /** 점수 이력의 한 점 (시각 오름차순 목록에 담는다) */
    private record Point(LocalDateTime at, BigDecimal score) {
    }

    @Override
    @Transactional
    public PmScoreIngestResponse receiveScores(PmScoreIngestRequest request) {
        List<PmScoreIngestItem> items = new ArrayList<>(request.getScores());
        items.sort(Comparator.comparing(PmScoreIngestItem::getPredictedAt));   // 같은 설비가 여러 건이면 시간 순서대로

        Set<Long> ids = items.stream().map(PmScoreIngestItem::getEquipId).collect(Collectors.toSet());
        Map<Long, EquipmentRow> equipments = equipmentRepository.findRowsByIds(ids).stream()
                .collect(Collectors.toMap(EquipmentRow::getEquipId, r -> r));
        Map<Long, List<PmThreshold>> thresholds = thresholdRepository.findByEquipIdIn(equipments.keySet()).stream()
                .collect(Collectors.groupingBy(PmThreshold::getEquipId));

        Map<Long, PmEvent> openEvents = new HashMap<>();      // 설비별 처리 중인 점수 이벤트 (미수신 이벤트는 별개라 뺀다)
        Map<Long, List<Point>> history = new HashMap<>();     // 설비별 최근 점수 (직전 점수, 24시간 최저 판단용)
        if (!request.isBackfill() && !items.isEmpty() && !equipments.isEmpty()) {
            for (PmEvent e : eventRepository.findByEquipIdInAndStatusIn(equipments.keySet(), OPEN_STATUSES)) {
                if (e.getTriggerType() != PmTriggerType.NO_DATA) {
                    openEvents.merge(e.getEquipId(), e, (a, b) -> a.getId() > b.getId() ? a : b);   // 혹시 여러 건이면 최신 것
                }
            }
            LocalDateTime from = items.get(0).getPredictedAt().minusHours(surgeWindowHours);
            for (PmScoreRepository.ScorePoint p : scoreRepository.findSince(equipments.keySet(), from)) {
                history.computeIfAbsent(p.getEquipId(), k -> new ArrayList<>())
                        .add(new Point(p.getPredictedAt(), p.getScore()));
            }
        }

        int saved = 0, duplicated = 0, unknown = 0, created = 0, raised = 0;
        Map<Long, PmStreamPayloads.Item> latest = new LinkedHashMap<>();   // SSE 용: 설비별 마지막 점수
        Map<Long, Long> regionOf = new HashMap<>();
        LocalDateTime lastAt = null;

        for (PmScoreIngestItem it : items) {
            EquipmentRow eq = equipments.get(it.getEquipId());
            if (eq == null) {
                unknown++;
                continue;
            }
            List<PmThreshold> rows = thresholds.get(eq.getEquipId());
            BigDecimal score = it.getScore().setScale(2, RoundingMode.HALF_UP);
            PmLevel level = judge(score, rows);
            int n = scoreRepository.insertIgnoreDuplicate(eq.getEquipId(), score, it.getPredictedAt(), level.getDbValue(),
                    toJson(it.getReasons() == null ? List.of() : it.getReasons()), toJson(it.getDetail()), it.getModelVersion());
            if (n == 0) {
                duplicated++;
                continue;
            }
            saved++;
            if (request.isBackfill()) {
                continue;
            }

            PmEquipmentType type = PmEquipmentType.valueOf(eq.getType());
            latest.put(eq.getEquipId(), new PmStreamPayloads.Item(eq.getEquipId(), eq.getName(), eq.getUnderpassId(), type,
                    score, level, false));
            regionOf.put(eq.getEquipId(), eq.getRegionId());
            lastAt = it.getPredictedAt();

            // 트리거 판단 (직전 점수와 24시간 최저는 이 점수를 넣기 전의 이력으로 본다)
            List<Point> points = history.computeIfAbsent(eq.getEquipId(), k -> new ArrayList<>());
            PmTriggerType trigger = trigger(it.getPredictedAt(), score, level, rows, points);
            points.add(new Point(it.getPredictedAt(), score));

            PmEvent open = openEvents.get(eq.getEquipId());
            if (open == null) {
                if (trigger == null) {
                    continue;   // 주의 단계에 머무는 것만으로는 이벤트를 만들지 않는다
                }
                PmEvent ev = new PmEvent();
                ev.setEquipId(eq.getEquipId());
                ev.setTriggerType(trigger);
                ev.setLevel(level);
                ev.setScore(score);
                ev.setStatus(PmEventStatus.CHECK_NEEDED);
                eventRepository.save(ev);
                openEvents.put(eq.getEquipId(), ev);
                created++;
                notify(eq, type, level, score, ev.getId(), trigger == PmTriggerType.SURGE ? "설비 점수 급상승" : "설비 위험 진입",
                        it.getReasons());
            } else if (open.getLevel() != null && level.ordinal() > open.getLevel().ordinal()) {
                open.setLevel(level);
                open.setScore(score);
                if (level == PmLevel.DANGER) {
                    open.setTriggerType(PmTriggerType.DANGER_ENTRY);   // 급상승으로 시작했어도 위험에 닿았으니 이유를 맞춘다
                }
                raised++;
                notify(eq, type, level, score, open.getId(), "설비 위험 상승", it.getReasons());
            }
        }

        engineMonitor.received(dbClock.now(), items.size(), request.isBackfill());

        // 실시간: 지역마다 한 번만
        if (!latest.isEmpty()) {
            Map<Long, List<PmStreamPayloads.Item>> byRegion = latest.values().stream()
                    .collect(Collectors.groupingBy(i -> regionOf.get(i.equipId()), LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<Long, List<PmStreamPayloads.Item>> e : byRegion.entrySet()) {
                streamPublisher.publishToAdmins(e.getKey(), "pm-update",
                        new PmStreamPayloads.PmUpdate(e.getKey(), lastAt, e.getValue()));
            }
        }
        return new PmScoreIngestResponse(items.size(), saved, duplicated, unknown, created, raised);
    }

    /**
     * 이벤트를 만들 이유가 있는지. 없으면 null.
     * 위험진입이 급상승보다 우선한다 (둘 다 해당하면 위험진입).
     */
    private PmTriggerType trigger(LocalDateTime at, BigDecimal score, PmLevel level, List<PmThreshold> rows, List<Point> points) {
        Point prev = null;
        BigDecimal min = null;
        LocalDateTime windowStart = at.minusHours(surgeWindowHours);
        for (Point p : points) {
            if (!p.at().isBefore(at)) {
                continue;   // 같은 시각 이후 값은 보지 않는다
            }
            if (prev == null || p.at().isAfter(prev.at())) {
                prev = p;
            }
            if (!p.at().isBefore(windowStart) && (min == null || p.score().compareTo(min) < 0)) {
                min = p.score();
            }
        }
        if (level == PmLevel.DANGER) {
            PmLevel prevLevel = prev == null ? PmLevel.SAFE : judge(prev.score(), rows);
            if (prevLevel != PmLevel.DANGER) {
                return PmTriggerType.DANGER_ENTRY;
            }
        }
        if (level.atLeast(PmLevel.CAUTION) && min != null && score.subtract(min).compareTo(surgeDelta) >= 0) {
            return PmTriggerType.SURGE;
        }
        return null;
    }

    /** 점수가 넘은 임계치 중 가장 높은 단계 (판정 규칙은 PmJudge, 임계치 수정 후 재판정과 같이 쓴다) */
    private PmLevel judge(BigDecimal score, List<PmThreshold> rows) {
        return PmJudge.judge(score, rows);
    }

    private void notify(EquipmentRow eq, PmEquipmentType type, PmLevel level, BigDecimal score, Long eventId, String title,
            List<String> reasons) {
        StringBuilder body = new StringBuilder(String.format("%s %s %s %s 단계 (%s점)", eq.getUnderpassName(), TYPE_KO.get(type),
                eq.getName(), level.getDbValue(), score.stripTrailingZeros().toPlainString()));
        if (reasons != null && !reasons.isEmpty() && reasons.get(0) != null && !reasons.get(0).isBlank()) {
            body.append(" · ").append(reasons.get(0).trim());
        }
        String text = body.length() > BODY_MAX ? body.substring(0, BODY_MAX) : body.toString();
        if (level == PmLevel.DANGER) {
            notificationService.notifyManagersUrgent(ManagerNotificationCategory.EQUIPMENT, eq.getRegionId(), title, text,
                    "PM_EVENT", eventId);
        } else {
            notificationService.notifyManagers(ManagerNotificationCategory.EQUIPMENT, eq.getRegionId(), title, text,
                    "PM_EVENT", eventId);
        }
    }

    private String toJson(Object value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }
}
