package com.mbc.puf.pm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.mbc.puf.pm.repository.PmActionRepository;
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
 *   ④-1 점검 후 쿨다운: 조치 완료(RESOLVED) 직후에는 센서 창에 점검 전 이상값이 남아 점수가 한동안 높게 나온다.
 *        그래서 조치 완료 뒤에는 (a) 점수가 한 번이라도 주의 미만으로 내려오거나 (b) 종류별 쿨다운 시간이 지나기 전까지
 *        새 이벤트·알림을 만들지 않는다 (점수는 그대로 저장되고 그래프도 이어진다).
 *        (b)로 풀렸는데 점수가 계속 위험이면 위험진입 이벤트를 1회 만든다 (점검했는데도 안 고쳐진 설비를 놓치지 않으려고).
 *        급상승의 '최저 점수'는 조치 완료 이후 점수만으로 계산하고, 위험진입은 위험 임계치보다 조금 내려와야(해제 여유) 해제로 본다.
 * 묶음이 끝나면 지역마다 SSE "pm-update" 를 한 번씩 보낸다.
 *
 * 부하를 줄이려고 묶음당 조회는 5번(설비, 임계치, 처리 중 이벤트, 최근 점수, 최근 조치 완료 시각)만 하고, 나머지는 메모리에서 판정한다.
 * 미수신(센서 끊김) 이벤트는 여기서 만들지 않는다 (PmStaleServiceImpl).
 */
@Service
public class PmIngestServiceImpl implements PmIngestService {

    private static final Logger log = LoggerFactory.getLogger(PmIngestServiceImpl.class);

    private static final List<PmEventStatus> OPEN_STATUSES = List.of(PmEventStatus.CHECK_NEEDED, PmEventStatus.IN_PROGRESS);
    private static final Map<PmEquipmentType, String> TYPE_KO = Map.of(
            PmEquipmentType.CCTV, "CCTV", PmEquipmentType.DISPLAY, "전광판",
            PmEquipmentType.PUMP, "배수펌프", PmEquipmentType.WATER_LEVEL, "수위센서");
    private static final int BODY_MAX = 250;   // manager_notification.body 는 255자

    private final PmEquipmentRepository equipmentRepository;
    private final PmScoreRepository scoreRepository;
    private final PmThresholdRepository thresholdRepository;
    private final PmEventRepository eventRepository;
    private final PmActionRepository actionRepository;
    private final NotificationService notificationService;
    private final StreamPublisher streamPublisher;
    private final ObjectMapper objectMapper;
    private final DbClock dbClock;
    private final PmEngineMonitor engineMonitor;
    private final BigDecimal surgeDelta;
    private final int surgeWindowHours;
    private final BigDecimal dangerReleaseMargin;           // 위험 임계치보다 이만큼 내려와야 '위험에서 벗어났다'고 본다
    private final Map<PmEquipmentType, Duration> cooldowns; // 종류별 조치 완료 후 쿨다운 (없거나 0 이면 쿨다운 안 함)
    private final Duration historyWindow;                   // 점수 이력을 읽어 올 길이 = max(급상승 구간, 가장 긴 쿨다운)

    public PmIngestServiceImpl(PmEquipmentRepository equipmentRepository, PmScoreRepository scoreRepository,
            PmThresholdRepository thresholdRepository, PmEventRepository eventRepository,
            PmActionRepository actionRepository,
            NotificationService notificationService, StreamPublisher streamPublisher, ObjectMapper objectMapper,
            DbClock dbClock, PmEngineMonitor engineMonitor,
            @Value("${puf.pm.surge-delta:25}") BigDecimal surgeDelta,
            @Value("${puf.pm.surge-window-hours:24}") int surgeWindowHours,
            @Value("${puf.pm.danger-release-margin:5}") BigDecimal dangerReleaseMargin,
            @Value("${puf.pm.cooldown.pump-minutes:10}") int pumpCooldownMinutes,
            @Value("${puf.pm.cooldown.cctv-hours:15}") int cctvCooldownHours,
            @Value("${puf.pm.cooldown.water-level-hours:26}") int waterLevelCooldownHours,
            @Value("${puf.pm.cooldown.display-hours:27}") int displayCooldownHours) {
        this.equipmentRepository = equipmentRepository;
        this.scoreRepository = scoreRepository;
        this.thresholdRepository = thresholdRepository;
        this.eventRepository = eventRepository;
        this.actionRepository = actionRepository;
        this.notificationService = notificationService;
        this.streamPublisher = streamPublisher;
        this.objectMapper = objectMapper;
        this.dbClock = dbClock;
        this.engineMonitor = engineMonitor;
        this.surgeDelta = surgeDelta;
        this.surgeWindowHours = Math.max(1, surgeWindowHours);
        this.dangerReleaseMargin = dangerReleaseMargin.max(BigDecimal.ZERO);
        this.cooldowns = new EnumMap<>(PmEquipmentType.class);
        this.cooldowns.put(PmEquipmentType.PUMP, Duration.ofMinutes(Math.max(0, pumpCooldownMinutes)));
        this.cooldowns.put(PmEquipmentType.CCTV, Duration.ofHours(Math.max(0, cctvCooldownHours)));
        this.cooldowns.put(PmEquipmentType.WATER_LEVEL, Duration.ofHours(Math.max(0, waterLevelCooldownHours)));
        this.cooldowns.put(PmEquipmentType.DISPLAY, Duration.ofHours(Math.max(0, displayCooldownHours)));
        Duration longest = Duration.ofHours(this.surgeWindowHours);
        for (Duration d : this.cooldowns.values()) {
            if (d.compareTo(longest) > 0) {
                longest = d;
            }
        }
        this.historyWindow = longest;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PmInternalEquipmentItem> getEquipments() {
        return equipmentRepository.findAllRows().stream()
                .filter(r -> !"ROAD".equals(r.getType()))
                .map(r -> new PmInternalEquipmentItem(r.getEquipId(), r.getName(), r.getType(), r.getUnderpassId(), r.getUnderpassName(),
                        r.getSensorIntervalSec(), r.getDataStartedAt(), r.getLastSensorAt(), r.getLastScoreAt()))
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
        Map<Long, List<Point>> history = new HashMap<>();     // 설비별 최근 점수 (직전 점수, 24시간 최저, 점검 후 쿨다운 판단용)
        Map<Long, PmActionRepository.MaintenancePoint> maintenance = new HashMap<>(); // 설비별 점검 이력 요약 (조치 완료 기록이 없으면 쿨다운 없음)
        if (!request.isBackfill() && !items.isEmpty() && !equipments.isEmpty()) {
            for (PmEvent e : eventRepository.findByEquipIdInAndStatusIn(equipments.keySet(), OPEN_STATUSES)) {
                if (e.getTriggerType() != PmTriggerType.NO_DATA) {
                    openEvents.merge(e.getEquipId(), e, (a, b) -> a.getId() > b.getId() ? a : b);   // 혹시 여러 건이면 최신 것
                }
            }
            LocalDateTime from = items.get(0).getPredictedAt().minus(historyWindow);
            for (PmScoreRepository.ScorePoint p : scoreRepository.findSince(equipments.keySet(), from)) {
                history.computeIfAbsent(p.getEquipId(), k -> new ArrayList<>())
                        .add(new Point(p.getPredictedAt(), p.getScore()));
            }
            for (PmActionRepository.MaintenancePoint m : actionRepository.findMaintenance(equipments.keySet())) {
                maintenance.put(m.getEquipId(), m);
            }
        }

        int saved = 0, duplicated = 0, unknown = 0, created = 0, raised = 0;
        Map<Long, PmStreamPayloads.Item> latest = new LinkedHashMap<>();   // SSE 용: 설비별 마지막 점수
        Map<Long, Long> regionOf = new HashMap<>();
        LocalDateTime lastAt = null;

        for (PmScoreIngestItem it : items) {
            EquipmentRow eq = equipments.get(it.getEquipId());
            if (eq == null || "ROAD".equals(eq.getType())) {
                unknown++;
                continue;
            }
            List<PmThreshold> rows = thresholds.get(eq.getEquipId());
            BigDecimal score = it.getScore().setScale(2, RoundingMode.HALF_UP);
            PmLevel level = judge(score, rows);
            int n = scoreRepository.insertIgnoreDuplicate(eq.getEquipId(), score, it.getPredictedAt(), level.getDbValue(),
                    toJson(it.getReasons() == null ? List.of() : it.getReasons()), toJson(it.getDetail()), it.getModelVersion());
            if (n == 0) {
                supplementComparisonSensors(it);
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
            PmActionRepository.MaintenancePoint maint = maintenance.get(eq.getEquipId());
            PmTriggerType trigger = trigger(eq.getEquipId(), type, it.getPredictedAt(), score, level, rows, points,
                    maint == null ? null : maint.getResolvedAt(), maint == null ? null : maint.getLastEventAt());
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

    void supplementComparisonSensors(PmScoreIngestItem item) {
        var sensors = item.getDetail() == null ? null : item.getDetail().path("extra").path("sensors");
        if (sensors != null && sensors.isArray() && !sensors.isEmpty()) {
            scoreRepository.supplementSensors(item.getEquipId(), item.getPredictedAt(), toJson(sensors));
        }
    }

    /**
     * 이벤트를 만들 이유가 있는지. 없으면 null.
     * 위험진입이 급상승보다 우선한다 (둘 다 해당하면 위험진입).
     *
     * 조치 완료(resolvedAt) 뒤에는 쿨다운이 걸린다 (클래스 설명의 ④-1):
     *   - 조치 완료 이후 점수가 한 번이라도 주의 미만으로 내려온 적이 있으면 → 풀림 (정상 판정)
     *   - 내려온 적이 없고 쿨다운 시간이 안 지났으면 → 이벤트를 만들지 않는다
     *   - 내려온 적이 없는데 쿨다운 시간이 지났으면 → 풀림. 이때 점수가 계속 위험이면 위험진입 1회
     *     (조치 완료 뒤에 이미 점수 이벤트가 한 번 만들어졌으면 다시 만들지 않는다. 기타 종료로 닫은 직후 반복되는 것을 막으려고)
     */
    private PmTriggerType trigger(Long equipId, PmEquipmentType type, LocalDateTime at, BigDecimal score, PmLevel level,
            List<PmThreshold> rows, List<Point> points, LocalDateTime resolvedAt, LocalDateTime lastEventAt) {
        LocalDateTime boundary = null;       // 급상승 '최저 점수'를 계산할 때 이 시각 이전 점수는 보지 않는다
        boolean releasedByTime = false;      // 점수는 안 내려왔지만 쿨다운 시간이 지나서 풀림
        Duration cooldown = cooldowns.get(type);
        if (resolvedAt != null && cooldown != null && !cooldown.isZero()) {
            boundary = resolvedAt;
            if (!dippedBelowCaution(at, resolvedAt, rows, points)) {
                if (at.isBefore(resolvedAt.plus(cooldown))) {
                    log.debug("[pm-cooldown] equip={} at={} score={} 조치 완료({}) 후 쿨다운 중 - 이벤트 만들지 않음",
                            equipId, at, score, resolvedAt);
                    return null;
                }
                releasedByTime = lastEventAt == null || !lastEventAt.isAfter(resolvedAt);   // 조치 완료 뒤 이벤트가 이미 있었으면 안전장치는 쓰지 않는다
            }
        }

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
            boolean inWindow = !p.at().isBefore(windowStart) && (boundary == null || p.at().isAfter(boundary));
            if (inWindow && (min == null || p.score().compareTo(min) < 0)) {
                min = p.score();
            }
        }
        if (level == PmLevel.DANGER) {
            // 직전 점수가 위험이거나, 위험 임계치 바로 아래(해제 여유 안)면 아직 위험에서 벗어난 게 아니라서 새로 들어온 게 아니다
            boolean prevInDanger = prev != null && inDangerZone(prev.score(), rows);
            if (!prevInDanger) {
                return PmTriggerType.DANGER_ENTRY;
            }
        }
        if (level.atLeast(PmLevel.CAUTION) && min != null && score.subtract(min).compareTo(surgeDelta) >= 0) {
            return PmTriggerType.SURGE;
        }
        if (releasedByTime && level == PmLevel.DANGER) {
            return PmTriggerType.DANGER_ENTRY;   // 점검 후 쿨다운이 끝났는데도 계속 위험 → 안 고쳐진 설비
        }
        return null;
    }

    /** 조치 완료 이후(resolvedAt < 시각 < at) 점수가 한 번이라도 주의 미만(안전 단계)으로 내려온 적이 있는지 */
    private boolean dippedBelowCaution(LocalDateTime at, LocalDateTime resolvedAt, List<PmThreshold> rows, List<Point> points) {
        for (Point p : points) {
            if (p.at().isAfter(resolvedAt) && p.at().isBefore(at) && judge(p.score(), rows) == PmLevel.SAFE) {
                return true;
            }
        }
        return false;
    }

    /** 위험 단계이거나, 위험 임계치보다 해제 여유(dangerReleaseMargin) 이내로만 내려온 점수 */
    private boolean inDangerZone(BigDecimal score, List<PmThreshold> rows) {
        if (judge(score, rows) == PmLevel.DANGER) {
            return true;
        }
        BigDecimal dangerMin = PmJudge.DEFAULT_DANGER;
        if (rows != null && !rows.isEmpty()) {
            BigDecimal found = null;
            for (PmThreshold t : rows) {
                if (t.getLevel() == PmLevel.DANGER && (found == null || t.getMinScore().compareTo(found) < 0)) {
                    found = t.getMinScore();
                }
            }
            if (found != null) {
                dangerMin = found;
            }
        }
        return score.compareTo(dangerMin.subtract(dangerReleaseMargin)) >= 0;
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
