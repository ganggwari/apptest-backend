package com.mbc.puf.handover.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.PmTriggerType;
import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.enums.UnderpassCurrentStatus;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.jpa.NativeSql;
import com.mbc.puf.common.request.PageParams;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.handover.dto.HandoverCreateRequest;
import com.mbc.puf.handover.dto.HandoverDetail;
import com.mbc.puf.handover.dto.HandoverListItem;
import com.mbc.puf.handover.dto.HandoverMemoRequest;
import com.mbc.puf.handover.dto.HandoverRows;
import com.mbc.puf.handover.dto.HandoverSummary;
import com.mbc.puf.handover.entity.ShiftHandover;
import com.mbc.puf.handover.repository.ShiftHandoverRepository;
import com.mbc.puf.member.entity.Manager;
import com.mbc.puf.member.entity.Shift;
import com.mbc.puf.member.repository.ManagerRepository;
import com.mbc.puf.member.repository.ShiftRepository;
import com.mbc.puf.pm.dto.PmEventItem;
import com.mbc.puf.pm.dto.PmPeriodSummary;
import com.mbc.puf.pm.service.PmReportService;
import com.mbc.puf.stream.StreamPublisher;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 근무조 인수인계 리포트.
 *
 *  - 근무 시간대는 shift.start_time ~ end_time (야간조처럼 끝 시각이 더 이르면 다음 날까지). shift_date 는 그 근무가 시작한 날.
 *  - 작성하면 그 시점의 집계(통제·위험 단계 변화·예지보전)를 JSON 으로 shift_handover.summary 에 스냅샷으로 저장한다.
 *  - 아직 처리 중인 점검(pm.openEvents)과 아직 차단·위험 단계인 지하차도는 해결될 때까지 다음 리포트에도 계속 나온다(이월).
 *  - 집계 범위는 쓰는(보는) 관리자의 담당 지역이다.
 *  - 로그인 직후 팝업은 getPrevious(): 내 근무조 바로 앞 근무조의 리포트. 안 쓴 경우에도 빈 화면 대신 그 시간대를 바로 집계해 준다.
 */
@Service
public class HandoverServiceImpl implements HandoverService {

    private static final int ACTIVITY_LIMIT = 100;
    private static final Duration NO_SHIFT_WINDOW = Duration.ofHours(8);   // 근무조가 없는 관리자의 기본 집계 길이

    @PersistenceContext
    private EntityManager em;

    private final ShiftHandoverRepository handoverRepository;
    private final ShiftRepository shiftRepository;
    private final ManagerRepository managerRepository;
    private final PmReportService pmReportService;
    private final ScopeResolver scopeResolver;
    private final StreamPublisher streamPublisher;
    private final DbClock dbClock;
    private final ObjectMapper objectMapper;

    public HandoverServiceImpl(ShiftHandoverRepository handoverRepository, ShiftRepository shiftRepository,
            ManagerRepository managerRepository, PmReportService pmReportService, ScopeResolver scopeResolver,
            StreamPublisher streamPublisher, DbClock dbClock, ObjectMapper objectMapper) {
        this.handoverRepository = handoverRepository;
        this.shiftRepository = shiftRepository;
        this.managerRepository = managerRepository;
        this.pmReportService = pmReportService;
        this.scopeResolver = scopeResolver;
        this.streamPublisher = streamPublisher;
        this.dbClock = dbClock;
        this.objectMapper = objectMapper;
    }

    /** 근무 시간대 한 번 (근무조 + 시작한 날 + 시작/끝 시각) */
    private record Window(Shift shift, LocalDate date, LocalDateTime start, LocalDateTime end) {
    }

    // ------------------------------------------------------------------ 직전 근무조 (로그인 팝업)
    @Override
    @Transactional(readOnly = true)
    public HandoverDetail getPrevious(LoginUser loginUser) {
        LocalDateTime now = dbClock.now();
        Manager me = managerRepository.findById(loginUser.id()).orElse(null);
        Shift myShift = me == null || me.getShiftId() == null ? null : shiftRepository.findById(me.getShiftId()).orElse(null);

        if (myShift == null) {
            // 근무조가 없으면: 가장 최근에 쓴 리포트, 그것도 없으면 최근 8시간 집계
            ShiftHandover latest = handoverRepository.findFirstByOrderByIdDesc().orElse(null);
            if (latest != null) {
                return toDetail(latest);
            }
            return computed(loginUser, null, null, now.minus(NO_SHIFT_WINDOW), now);
        }
        Window prev = previousWindow(myShift, now);
        if (prev == null) {   // 근무조가 하나뿐이라 "직전"이 따로 없음
            return computed(loginUser, null, null, now.minus(NO_SHIFT_WINDOW), now);
        }
        ShiftHandover written = handoverRepository
                .findFirstByShiftIdAndShiftDateOrderByIdDesc(prev.shift().getId(), prev.date()).orElse(null);
        if (written != null) {
            return toDetail(written);
        }
        return computed(loginUser, prev.shift(), prev.date(), prev.start(), prev.end());
    }

    // ------------------------------------------------------------------ 미리보기
    @Override
    @Transactional(readOnly = true)
    public HandoverDetail preview(LoginUser loginUser, Long shiftId) {
        Window w = resolveWindow(loginUser, shiftId, null);
        LocalDateTime now = dbClock.now();
        LocalDateTime end = w.end().isAfter(now) ? now : w.end();
        return computed(loginUser, w.shift(), w.date(), w.start(), end);
    }

    // ------------------------------------------------------------------ 작성
    @Override
    @Transactional
    public HandoverDetail create(LoginUser loginUser, HandoverCreateRequest request) {
        Window w = resolveWindow(loginUser, request.getShiftId(), request.getShiftDate());
        LocalDateTime now = dbClock.now();
        LocalDateTime end = w.end().isAfter(now) ? now : w.end();
        HandoverSummary summary = buildSummary(scopeResolver.regionsOf(loginUser, null), w.shift().getId(), w.start(), end);

        ShiftHandover h = new ShiftHandover();
        h.setShiftId(w.shift().getId());
        h.setManagerId(loginUser.id());
        h.setShiftDate(w.date());
        h.setSummary(objectMapper.writeValueAsString(summary));
        h.setMemo(blankToNull(request.getMemo()));
        handoverRepository.saveAndFlush(h);
        em.refresh(h);

        streamPublisher.dataChangedToAdmins(null, "shift-handover", "CREATE", h.getId());
        return toDetail(h);
    }

    // ------------------------------------------------------------------ 조회·수정
    @Override
    @Transactional(readOnly = true)
    public PageResponse<HandoverListItem> list(Long shiftId, PeriodQuery period, PageQuery page) {
        period.validate();
        PageParams p = page.toParams();
        String from = """
                 FROM shift_handover h
                 JOIN shift s ON s.id = h.shift_id
                 LEFT JOIN manager m ON m.id = h.manager_id
                 LEFT JOIN employee emp ON emp.id = m.employee_id
                 WHERE 1 = 1
                """;
        NativeSql rows = filters(new NativeSql("""
                SELECT h.id AS id, h.shift_id AS shift_id, s.name AS shift_name, h.shift_date AS shift_date,
                       h.manager_id AS manager_id, emp.name AS author_name, h.memo AS memo, h.created_at AS created_at
                """ + from), shiftId, period)
                .append(" ORDER BY h.shift_date DESC, h.id DESC LIMIT :size OFFSET :offset")
                .param("size", p.getSize()).param("offset", p.getOffset());
        NativeSql count = filters(new NativeSql("SELECT COUNT(*) AS total" + from), shiftId, period);
        return new PageResponse<>(rows.list(em, HandoverListItem.class), p.getPage(), p.getSize(),
                count.single(em, com.mbc.puf.pm.service.PmQueryServiceImpl.Total.class).getTotal());
    }

    private NativeSql filters(NativeSql sql, Long shiftId, PeriodQuery period) {
        return sql.andIf(shiftId != null, " AND h.shift_id = :shiftId", "shiftId", shiftId)
                .andIf(period.getStartDate() != null, " AND h.shift_date >= :startDate", "startDate", period.getStartDate())
                .andIf(period.getEndDate() != null, " AND h.shift_date <= :endDate", "endDate", period.getEndDate());
    }

    @Override
    @Transactional(readOnly = true)
    public HandoverDetail get(Long id) {
        return toDetail(find(id));
    }

    @Override
    @Transactional
    public HandoverDetail updateMemo(LoginUser loginUser, Long id, HandoverMemoRequest request) {
        ShiftHandover h = find(id);
        if (!loginUser.id().equals(h.getManagerId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "작성한 사람만 메모를 고칠 수 있습니다.");
        }
        h.setMemo(blankToNull(request.getMemo()));
        handoverRepository.save(h);
        streamPublisher.dataChangedToAdmins(null, "shift-handover", "UPDATE", id);
        return toDetail(h);
    }

    // ------------------------------------------------------------------ 집계
    private HandoverSummary buildSummary(List<Long> regionIds, Long shiftId, LocalDateTime start, LocalDateTime end) {
        PmPeriodSummary pm = pmReportService.summarize(regionIds, null, start, end);

        List<HandoverRows.EventRow> events = new NativeSql("""
                SELECT ev.created_at AS created_at, CAST(ev.type AS text) AS type, CAST(ev.risk_level AS text) AS risk_level,
                       u.name AS underpass_name, ev.is_auto AS automatic, ev.reason AS reason, oemp.name AS operator_name
                  FROM event ev JOIN underpass u ON u.id = ev.underpass_id AND u.is_deleted = FALSE
                  LEFT JOIN manager om ON om.id = ev.operator_id
                  LEFT JOIN employee oemp ON oemp.id = om.employee_id
                 WHERE ev.created_at >= :from AND ev.created_at < :to AND u.region_id IN (:regionIds)
                 ORDER BY ev.created_at DESC, ev.id DESC
                """).param("from", start).param("to", end).param("regionIds", regionIds)
                .list(em, HandoverRows.EventRow.class);
        int block = 0, release = 0, riskUp = 0, riskDown = 0;
        for (HandoverRows.EventRow e : events) {
            switch (e.getType()) {
                case "BLOCK" -> block++;
                case "RELEASE" -> release++;
                case "RISK_LEVEL_UP" -> riskUp++;
                case "RISK_LEVEL_DOWN" -> riskDown++;
                default -> { }
            }
        }

        List<HandoverRows.UnderpassRow> now = new NativeSql("""
                SELECT u.id AS underpass_id, u.name AS name, CAST(u.risk_level AS text) AS risk_level,
                       CAST(u.status AS text) AS status
                  FROM underpass u
                 WHERE u.is_deleted = FALSE AND u.region_id IN (:regionIds)
                   AND (CAST(u.status AS text) = 'BLOCKED' OR CAST(u.risk_level AS text) <> '안전')
                 ORDER BY u.id
                """).param("regionIds", regionIds).list(em, HandoverRows.UnderpassRow.class);
        List<HandoverSummary.UnderpassState> blocked = new ArrayList<>();
        List<HandoverSummary.UnderpassState> elevated = new ArrayList<>();
        for (HandoverRows.UnderpassRow u : now) {
            var state = new HandoverSummary.UnderpassState(u.getUnderpassId(), u.getName(), u.getRiskLevel());
            if (u.getStatus() == UnderpassCurrentStatus.BLOCKED) {
                blocked.add(state);
            } else if (u.getRiskLevel() != RiskLevel.SAFE) {
                elevated.add(state);
            }
        }

        List<HandoverSummary.Activity> activities = new ArrayList<>();
        for (HandoverRows.EventRow e : events) {
            activities.add(riskActivity(e));
        }
        pm.newEvents().forEach(ev -> activities.add(new HandoverSummary.Activity(ev.getCreatedAt(), "설비", pmEventText(ev), null)));
        pm.actions().forEach(a -> activities.add(new HandoverSummary.Activity(a.getCreatedAt(), "점검",
                String.format("%s %s 점검 기록: %s", a.getCode(), a.getUnderpassName(), a.getContent()), a.getManagerName())));
        List<HandoverRows.DisplayEditRow> displayEdits = new NativeSql("""
                SELECT h.changed_at AS changed_at, u.name AS underpass_name, h.new_content AS new_content, emp.name AS actor_name
                  FROM display_history h
                  JOIN underpass u ON u.id = h.underpass_id AND u.is_deleted = FALSE
                  LEFT JOIN manager m ON m.id = h.changed_by
                  LEFT JOIN employee emp ON emp.id = m.employee_id
                 WHERE h.event_id IS NULL AND h.changed_by IS NOT NULL
                   AND h.changed_at >= :from AND h.changed_at < :to AND u.region_id IN (:regionIds)
                 ORDER BY h.changed_at DESC, h.id DESC
                """).param("from", start).param("to", end).param("regionIds", regionIds)
                .list(em, HandoverRows.DisplayEditRow.class);
        displayEdits.forEach(d -> activities.add(new HandoverSummary.Activity(d.getChangedAt(), "전광판",
                d.getUnderpassName() + " 전광판 문구 수정: " + d.getNewContent(), d.getActorName())));
        activities.sort(Comparator.comparing(HandoverSummary.Activity::at).reversed());

        List<HandoverSummary.StaffWork> staff = staffWork(regionIds, shiftId, start, end);

        var counts = new HandoverSummary.Counts(block, release, riskUp, riskDown, pm.newEventCount(),
                pm.actionCount(), pm.openEventCount());
        return new HandoverSummary(start, end, counts, blocked, elevated, pm, staff,
                activities.size() > ACTIVITY_LIMIT ? activities.subList(0, ACTIVITY_LIMIT) : activities);
    }

    private HandoverSummary.Activity riskActivity(HandoverRows.EventRow e) {
        String auto = Boolean.TRUE.equals(e.getAutomatic()) ? " (자동)" : "";
        String reason = e.getReason() == null || e.getReason().isBlank() ? "" : " · " + e.getReason();
        return switch (e.getType()) {
            case "BLOCK" -> new HandoverSummary.Activity(e.getCreatedAt(), "통제", e.getUnderpassName() + " 통제 시작" + auto + reason,
                    e.getOperatorName());
            case "RELEASE" -> new HandoverSummary.Activity(e.getCreatedAt(), "통제", e.getUnderpassName() + " 통제 해제" + auto + reason,
                    e.getOperatorName());
            case "RISK_LEVEL_UP" -> new HandoverSummary.Activity(e.getCreatedAt(), "위험",
                    e.getUnderpassName() + " 위험 단계 상승 → " + levelLabel(e.getRiskLevel()), null);
            default -> new HandoverSummary.Activity(e.getCreatedAt(), "위험",
                    e.getUnderpassName() + " 위험 단계 하강 → " + levelLabel(e.getRiskLevel()), null);
        };
    }

    /** 새 점검 대기 한 줄. 미수신은 점수·단계가 없다 */
    private String pmEventText(PmEventItem ev) {
        if (ev.getTriggerType() == PmTriggerType.NO_DATA) {
            return String.format("%s %s 센서 데이터 미수신 점검 대기 등록", ev.getCode(), ev.getUnderpassName());
        }
        String why = ev.getTriggerType() == null ? "" : ev.getTriggerType().getDbValue() + ", ";
        return String.format("%s %s 점검 대기 등록 (%s%s, %s점)", ev.getCode(), ev.getUnderpassName(), why,
                ev.getLevel() == null ? "-" : ev.getLevel().getDbValue(),
                ev.getScore() == null ? "-" : ev.getScore().stripTrailingZeros().toPlainString());
    }

    /**
     * 이 시간대에 직원별로 한 일. 근무조 소속(내 담당 지역과 겹치는 사람)과 실제로 처리한 사람을 합친다.
     * 지역을 가리는 항목은 내 담당 지역 안에서 센다 (접수의견 답변만 지역 구분 없음).
     */
    private List<HandoverSummary.StaffWork> staffWork(List<Long> regionIds, Long shiftId, LocalDateTime start, LocalDateTime end) {
        List<HandoverRows.StaffCountRow> counted = new NativeSql("""
                SELECT t.manager_id AS manager_id, t.kind AS kind, COUNT(*) AS cnt FROM (
                  SELECT ev.operator_id AS manager_id,
                         CASE WHEN CAST(ev.type AS text) = 'BLOCK' THEN 'block' ELSE 'release' END AS kind
                    FROM event ev JOIN underpass u ON u.id = ev.underpass_id AND u.is_deleted = FALSE
                   WHERE ev.operator_id IS NOT NULL AND CAST(ev.type AS text) IN ('BLOCK', 'RELEASE')
                     AND ev.created_at >= :from AND ev.created_at < :to AND u.region_id IN (:regionIds)
                  UNION ALL
                  SELECT p.reviewed_by, 'plateReview'
                    FROM plate_log p JOIN camera c ON c.id = p.camera_id JOIN underpass u ON u.id = c.underpass_id
                   WHERE p.reviewed_by IS NOT NULL AND p.reviewed_at >= :from AND p.reviewed_at < :to AND u.region_id IN (:regionIds)
                  UNION ALL
                  SELECT a.manager_id, 'pmAction'
                    FROM pm_action a JOIN pm_event pe ON pe.id = a.event_id JOIN pm_equipment e ON e.id = pe.equip_id
                    JOIN underpass u ON u.id = e.underpass_id
                   WHERE a.created_at >= :from AND a.created_at < :to AND u.region_id IN (:regionIds)
                  UNION ALL
                  SELECT h.changed_by, 'displayEdit'
                    FROM display_history h JOIN underpass u ON u.id = h.underpass_id
                   WHERE h.event_id IS NULL AND h.changed_by IS NOT NULL
                     AND h.changed_at >= :from AND h.changed_at < :to AND u.region_id IN (:regionIds)
                  UNION ALL
                  SELECT ct.manager_id, 'transfer'
                    FROM ctrl_transfer ct JOIN event ev ON ev.id = ct.event_id JOIN underpass u ON u.id = ev.underpass_id
                   WHERE ct.manager_id IS NOT NULL AND ct.created_at >= :from AND ct.created_at < :to AND u.region_id IN (:regionIds)
                  UNION ALL
                  SELECT i.manager_id, 'inquiryAnswer'
                    FROM inquiry i
                   WHERE i.manager_id IS NOT NULL AND i.is_deleted = FALSE AND i.created_at >= :from AND i.created_at < :to
                ) t
                GROUP BY t.manager_id, t.kind
                """).param("from", start).param("to", end).param("regionIds", regionIds)
                .list(em, HandoverRows.StaffCountRow.class);

        Map<Long, Map<String, Integer>> byManager = new LinkedHashMap<>();
        for (HandoverRows.StaffCountRow r : counted) {
            byManager.computeIfAbsent(r.getManagerId(), k -> new HashMap<>()).put(r.getKind(), r.getCnt().intValue());
        }
        Set<Long> assigned = new HashSet<>();
        if (shiftId != null) {
            for (HandoverRows.StaffInfoRow r : new NativeSql("""
                    SELECT m.id AS manager_id, emp.name AS name, emp.phone AS phone
                      FROM manager m JOIN employee emp ON emp.id = m.employee_id
                     WHERE m.shift_id = :shiftId AND CAST(m.status AS text) = 'ACTIVE'
                       AND EXISTS (SELECT 1 FROM manager_region mr WHERE mr.manager_id = m.id AND mr.region_id IN (:regionIds))
                    """).param("shiftId", shiftId).param("regionIds", regionIds).list(em, HandoverRows.StaffInfoRow.class)) {
                assigned.add(r.getManagerId());
                byManager.computeIfAbsent(r.getManagerId(), k -> new HashMap<>());
            }
        }
        if (byManager.isEmpty()) {
            return List.of();
        }
        Map<Long, HandoverRows.StaffInfoRow> info = new HashMap<>();
        for (HandoverRows.StaffInfoRow r : new NativeSql("""
                SELECT m.id AS manager_id, emp.name AS name, emp.phone AS phone
                  FROM manager m JOIN employee emp ON emp.id = m.employee_id WHERE m.id IN (:ids)
                """).param("ids", byManager.keySet()).list(em, HandoverRows.StaffInfoRow.class)) {
            info.put(r.getManagerId(), r);
        }
        List<HandoverSummary.StaffWork> result = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Integer>> e : byManager.entrySet()) {
            Map<String, Integer> c = e.getValue();
            int block = c.getOrDefault("block", 0), release = c.getOrDefault("release", 0),
                    plate = c.getOrDefault("plateReview", 0), pmAction = c.getOrDefault("pmAction", 0),
                    display = c.getOrDefault("displayEdit", 0), transfer = c.getOrDefault("transfer", 0),
                    inquiry = c.getOrDefault("inquiryAnswer", 0);
            HandoverRows.StaffInfoRow i = info.get(e.getKey());
            result.add(new HandoverSummary.StaffWork(e.getKey(), i == null ? null : i.getName(), i == null ? null : i.getPhone(),
                    assigned.contains(e.getKey()), block, release, plate, pmAction, display, transfer, inquiry,
                    block + release + plate + pmAction + display + transfer + inquiry));
        }
        result.sort(Comparator.comparingInt(HandoverSummary.StaffWork::total).reversed()
                .thenComparing(HandoverSummary.StaffWork::managerId));
        return result;
    }

    private String levelLabel(RiskLevel level) {
        return level == null ? "-" : level.getDbValue();
    }

    // ------------------------------------------------------------------ 근무 시간대
    private Window windowFor(Shift shift, LocalDate date) {
        LocalDateTime start = date.atTime(shift.getStartTime());
        LocalDateTime end = shift.getEndTime().isAfter(shift.getStartTime())
                ? date.atTime(shift.getEndTime()) : date.plusDays(1).atTime(shift.getEndTime());
        return new Window(shift, date, start, end);
    }

    /** 지금 시각 기준 이 근무조의 가장 최근에 시작한 근무 (오늘 시작분, 아직 안 시작했으면 어제 시작분) */
    private Window currentWindow(Shift shift, LocalDateTime now) {
        Window today = windowFor(shift, now.toLocalDate());
        return today.start().isAfter(now) ? windowFor(shift, now.toLocalDate().minusDays(1)) : today;
    }

    /** 이 근무조 바로 앞 근무조의, 이 근무조의 가장 최근 근무가 시작하기 직전에 끝난 근무 */
    private Window previousWindow(Shift shift, LocalDateTime now) {
        List<Shift> shifts = shiftRepository.findAllByOrderByStartTimeAsc();
        if (shifts.size() < 2) {
            return null;
        }
        int idx = 0;
        for (int i = 0; i < shifts.size(); i++) {
            if (shifts.get(i).getId().equals(shift.getId())) {
                idx = i;
            }
        }
        Shift prev = shifts.get((idx - 1 + shifts.size()) % shifts.size());
        Window cur = currentWindow(shift, now);
        LocalDateTime end = cur.start();
        Duration length = Duration.between(LocalDate.EPOCH.atTime(prev.getStartTime()),
                prev.getEndTime().isAfter(prev.getStartTime()) ? LocalDate.EPOCH.atTime(prev.getEndTime())
                        : LocalDate.EPOCH.plusDays(1).atTime(prev.getEndTime()));
        LocalDateTime start = end.minus(length);
        return new Window(prev, start.toLocalDate(), start, end);
    }

    /** 요청의 근무조·날짜를 실제 근무 시간대로 바꾼다. 근무조를 정할 수 없으면 400 */
    private Window resolveWindow(LoginUser loginUser, Long shiftId, LocalDate shiftDate) {
        Long resolved = shiftId != null ? shiftId
                : managerRepository.findById(loginUser.id()).map(Manager::getShiftId).orElse(null);
        if (resolved == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "근무조가 정해지지 않았습니다. shiftId를 보내 주세요.");
        }
        Shift shift = shiftRepository.findById(resolved).orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 근무조입니다. id=" + resolved));
        LocalDateTime now = dbClock.now();
        if (shiftDate == null) {
            return currentWindow(shift, now);
        }
        if (shiftDate.isAfter(now.toLocalDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "미래 날짜의 근무는 집계할 수 없습니다.");
        }
        return windowFor(shift, shiftDate);
    }

    // ------------------------------------------------------------------ 응답 만들기
    private ShiftHandover find(Long id) {
        return handoverRepository.findById(id).orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 인수인계 리포트입니다. id=" + id));
    }

    private HandoverDetail toDetail(ShiftHandover h) {
        Shift shift = shiftRepository.findById(h.getShiftId()).orElse(null);
        String[] author = author(h.getManagerId());
        JsonNode summary = h.getSummary() == null ? null : objectMapper.readTree(h.getSummary());
        Window w = shift == null ? null : windowFor(shift, h.getShiftDate());
        return new HandoverDetail(h.getId(), true, h.getShiftId(), shift == null ? null : shift.getName(), h.getShiftDate(),
                shift == null ? null : shift.getStartTime(), shift == null ? null : shift.getEndTime(),
                w == null ? null : w.start(), w == null ? null : w.end(), h.getManagerId(),
                author[0], author[1], h.getMemo(), h.getCreatedAt(), summary);
    }

    /** 아직 아무도 안 쓴 시간대를 바로 집계한 결과 (저장 안 함) */
    private HandoverDetail computed(LoginUser loginUser, Shift shift, LocalDate date, LocalDateTime start, LocalDateTime end) {
        HandoverSummary summary = buildSummary(scopeResolver.regionsOf(loginUser, null), shift == null ? null : shift.getId(), start, end);
        return new HandoverDetail(null, false, shift == null ? null : shift.getId(), shift == null ? null : shift.getName(), date,
                shift == null ? null : shift.getStartTime(), shift == null ? null : shift.getEndTime(), start, end,
                null, null, null, null, null, objectMapper.valueToTree(summary));
    }

    /** [이름, 연락처] — 인수인계 받는 사람이 연락할 수 있게 가리지 않는다 */
    private String[] author(Long managerId) {
        if (managerId == null) {
            return new String[]{null, null};
        }
        List<?> rows = em.createNativeQuery("""
                SELECT emp.name, emp.phone FROM manager m JOIN employee emp ON emp.id = m.employee_id WHERE m.id = :id
                """).setParameter("id", managerId).getResultList();
        if (rows.isEmpty()) {
            return new String[]{null, null};
        }
        Object[] r = (Object[]) rows.get(0);
        return new String[]{r[0] == null ? null : r[0].toString(), r[1] == null ? null : r[1].toString()};
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
