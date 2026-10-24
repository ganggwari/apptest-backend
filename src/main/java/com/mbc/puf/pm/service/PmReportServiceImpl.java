package com.mbc.puf.pm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.enums.PmLevel;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.jpa.NativeSql;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.pm.dto.PmActionItem;
import com.mbc.puf.pm.dto.PmDailyReport;
import com.mbc.puf.pm.dto.PmEventItem;
import com.mbc.puf.pm.dto.PmLevelCounts;
import com.mbc.puf.pm.dto.PmPeriodSummary;
import com.mbc.puf.pm.dto.PmReportEquipment;
import com.mbc.puf.pm.dto.PmTypeCounts;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 예지보전 리포트 집계. 저장하지 않고 요청할 때마다 pm_score / pm_event / pm_action 에서 계산한다.
 *
 * 설비 상태는 "기준 시각 이전 가장 최근 점수"로 본다. 점수 시각은 그 설비 센서의 마지막 측정 시각이라, 기준 시각보다
 * 설비 종류별 기준(센서 주기 × puf.pm.stale.multiplier(기본 3), 최소 puf.pm.stale.min-seconds(기본 60초):
 * 펌프 1분 / 수위 15분 / CCTV·전광판 3시간) 넘게 오래됐으면 미수신(noData), 점수가 아예 없으면 neverReceived.
 */
@Service
public class PmReportServiceImpl implements PmReportService {

    private static final int LIST_LIMIT = 100;
    private static final int RECOMMENDED_LIMIT = 10;
    private static final int STALE_LIMIT = 30;

    private static final String SNAPSHOT_SQL = """
            SELECT e.id AS equip_id, CAST(e.type AS text) AS type, e.name AS name, u.id AS underpass_id, u.name AS underpass_name,
                   u.region_id AS region_id, ls.score AS score, CAST(ls.level AS text) AS level,
                   ls.predicted_at AS last_received_at, e.sensor_interval_sec AS sensor_interval_sec, oe.id AS open_event_id, CAST(oe.status AS text) AS open_event_status
             FROM pm_equipment e
             JOIN underpass u ON u.id = e.underpass_id AND u.is_deleted = FALSE AND e.is_deleted = FALSE
             LEFT JOIN LATERAL (SELECT s.score, s.level, s.predicted_at FROM pm_score s
                                 WHERE s.equip_id = e.id AND s.predicted_at < :asOf
                                 ORDER BY s.predicted_at DESC LIMIT 1) ls ON TRUE
             LEFT JOIN LATERAL (SELECT ev.id, ev.status FROM pm_event ev
                                 WHERE ev.equip_id = e.id AND ev.created_at < :asOf
                                   AND CAST(ev.status AS text) IN ('CHECK_NEEDED', 'IN_PROGRESS')
                                 ORDER BY ev.id DESC LIMIT 1) oe ON TRUE
             WHERE 1 = 1
            """;

    private static final String EVENT_SELECT = """
            SELECT ev.id AS event_id, e.id AS equip_id, CAST(e.type AS text) AS type, e.name AS name, u.id AS underpass_id,
                   u.name AS underpass_name, u.region_id AS region_id, CAST(ev.trigger_type AS text) AS trigger_type, CAST(ev.level AS text) AS level, ev.score AS score,
                   CAST(ev.status AS text) AS status, ev.created_at AS created_at,
                   ls.score AS current_score, CAST(ls.level AS text) AS current_level
             FROM pm_event ev
             JOIN pm_equipment e ON e.id = ev.equip_id
             JOIN underpass u ON u.id = e.underpass_id AND u.is_deleted = FALSE AND e.is_deleted = FALSE
             LEFT JOIN LATERAL (SELECT s.score, s.level FROM pm_score s WHERE s.equip_id = e.id
                                 ORDER BY s.predicted_at DESC LIMIT 1) ls ON TRUE
             WHERE 1 = 1
            """;

    private static final String ACTION_SELECT = """
            SELECT a.id AS action_id, ev.id AS event_id, e.id AS equip_id, CAST(e.type AS text) AS type, e.name AS name,
                   u.id AS underpass_id, u.name AS underpass_name, u.region_id AS region_id, a.content AS content,
                   a.manager_id AS manager_id, emp.name AS manager_name, a.created_at AS created_at,
                   CAST(ev.level AS text) AS event_level, ev.score AS score_before,
                   (SELECT s.score FROM pm_score s WHERE s.equip_id = e.id AND s.predicted_at > a.created_at
                     ORDER BY s.predicted_at ASC LIMIT 1) AS score_after
             FROM pm_action a
             JOIN pm_event ev ON ev.id = a.event_id
             JOIN pm_equipment e ON e.id = ev.equip_id
             JOIN underpass u ON u.id = e.underpass_id AND u.is_deleted = FALSE AND e.is_deleted = FALSE
             LEFT JOIN manager m ON m.id = a.manager_id
             LEFT JOIN employee emp ON emp.id = m.employee_id
             WHERE 1 = 1
            """;

    @PersistenceContext
    private EntityManager em;

    private final ScopeResolver scopeResolver;
    private final DbClock dbClock;
    private final int multiplier;
    private final int minSeconds;

    public PmReportServiceImpl(ScopeResolver scopeResolver, DbClock dbClock,
                               @Value("${puf.pm.stale.multiplier:3}") int multiplier,
                               @Value("${puf.pm.stale.min-seconds:60}") int minSeconds) {
        this.scopeResolver = scopeResolver;
        this.dbClock = dbClock;
        this.multiplier = Math.max(1, multiplier);
        this.minSeconds = Math.max(1, minSeconds);
    }

    @Override
    @Transactional(readOnly = true)
    public PmDailyReport getDailyReport(LoginUser loginUser, ScopeQuery scope, LocalDate date) {
        ScopeQuery resolved = scopeResolver.resolve(loginUser, scope);
        return buildDailyReport(resolved.getRegionIds(), resolved.getUnderpassId(), date);
    }

    @Override
    @Transactional(readOnly = true)
    public PmDailyReport buildDailyReport(List<Long> regionIds, Long underpassId, LocalDate date) {
        LocalDateTime now = dbClock.now();
        LocalDate day = date == null ? now.toLocalDate() : date;
        if (day.isAfter(now.toLocalDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "미래 날짜의 리포트는 만들 수 없습니다.");
        }
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();
        if (to.isAfter(now)) {
            to = now;   // 오늘이면 지금까지
        }
        return new PmDailyReport(day, now, summarize(regionIds, underpassId, from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public PmPeriodSummary summarize(List<Long> regionIds, Long underpassId, LocalDateTime from, LocalDateTime to) {
        List<PmReportEquipment> atEnd = snapshot(regionIds, underpassId, to);
        List<PmReportEquipment> atStart = snapshot(regionIds, underpassId, from);

        List<PmReportEquipment> stale = new ArrayList<>();
        int never = 0;
        for (PmReportEquipment e : atEnd) {
            if (e.getLastReceivedAt() == null) {
                never++;
            } else if (isStale(e, to)) {
                stale.add(e);
            }
        }
        stale.sort(Comparator.comparing(PmReportEquipment::getLastReceivedAt));
        List<PmReportEquipment> recommended = atEnd.stream()
                .filter(e -> e.getLevel() != null && !isStale(e, to) && e.getLevel().atLeast(PmLevel.CAUTION))
                .sorted(Comparator.comparing(PmReportEquipment::getScore).reversed())
                .limit(RECOMMENDED_LIMIT).toList();

        List<PmEventItem> newEvents = eventsSql(regionIds, underpassId)
                .append(" AND ev.created_at >= :from AND ev.created_at < :to ORDER BY ev.created_at DESC, ev.id DESC LIMIT " + LIST_LIMIT)
                .param("from", from).param("to", to).list(em, PmEventItem.class);
        int newCount = countBetween("pm_event ev JOIN pm_equipment e ON e.id = ev.equip_id", "ev.created_at", regionIds, underpassId, from, to);

        List<PmActionItem> actions = actionsSql(regionIds, underpassId)
                .append(" AND a.created_at >= :from AND a.created_at < :to ORDER BY a.created_at DESC, a.id DESC LIMIT " + LIST_LIMIT)
                .param("from", from).param("to", to).list(em, PmActionItem.class);
        int actionCount = countBetween("pm_action a JOIN pm_event ev ON ev.id = a.event_id JOIN pm_equipment e ON e.id = ev.equip_id",
                "a.created_at", regionIds, underpassId, from, to);

        List<PmEventItem> open = eventsSql(regionIds, underpassId)
                .append(" AND CAST(ev.status AS text) IN ('CHECK_NEEDED', 'IN_PROGRESS') ORDER BY ev.created_at ASC, ev.id ASC LIMIT " + LIST_LIMIT)
                .list(em, PmEventItem.class);

        return new PmPeriodSummary(from, to, counts(atEnd, to), counts(atStart, from), byType(atEnd, to),
                newCount, newEvents, actionCount, actions, open.size(), open,
                stale.stream().limit(STALE_LIMIT).toList(), never, recommended);
    }

    // ------------------------------------------------------------------

    private boolean isStale(PmReportEquipment e, LocalDateTime asOf) {
        if (e.getLastReceivedAt() == null) {
            return false;
        }
        long threshold = Math.max((long) e.getSensorIntervalSec() * multiplier, minSeconds);   // 설비 종류(센서 주기)별 기준
        return e.getLastReceivedAt().plusSeconds(threshold).isBefore(asOf);
    }

    private PmLevelCounts counts(List<PmReportEquipment> rows, LocalDateTime asOf) {
        int safe = 0, caution = 0, danger = 0, noData = 0;
        for (PmReportEquipment e : rows) {
            if (e.getLevel() == null || isStale(e, asOf)) {
                noData++;
            } else if (e.getLevel() == PmLevel.DANGER) {
                danger++;
            } else if (e.getLevel() == PmLevel.CAUTION) {
                caution++;
            } else {
                safe++;
            }
        }
        return new PmLevelCounts(safe, caution, danger, noData);
    }

    private List<PmTypeCounts> byType(List<PmReportEquipment> rows, LocalDateTime asOf) {
        Map<PmEquipmentType, List<PmReportEquipment>> grouped = new EnumMap<>(PmEquipmentType.class);
        for (PmReportEquipment e : rows) {
            grouped.computeIfAbsent(e.getType(), k -> new ArrayList<>()).add(e);
        }
        List<PmTypeCounts> result = new ArrayList<>();
        grouped.forEach((type, list) -> {
            PmLevelCounts c = counts(list, asOf);
            result.add(new PmTypeCounts(type, list.size(), c.safe(), c.caution(), c.danger(), c.noData()));
        });
        return result;
    }

    private List<PmReportEquipment> snapshot(List<Long> regionIds, Long underpassId, LocalDateTime asOf) {
        return new NativeSql(SNAPSHOT_SQL)
                .andIf(regionIds != null, " AND u.region_id IN (:regionIds)", "regionIds", regionIds)
                .andIf(underpassId != null, " AND u.id = :underpassId", "underpassId", underpassId)
                .append(" ORDER BY e.id").param("asOf", asOf).list(em, PmReportEquipment.class);
    }

    private NativeSql eventsSql(List<Long> regionIds, Long underpassId) {
        return new NativeSql(EVENT_SELECT)
                .andIf(regionIds != null, " AND u.region_id IN (:regionIds)", "regionIds", regionIds)
                .andIf(underpassId != null, " AND u.id = :underpassId", "underpassId", underpassId);
    }

    private NativeSql actionsSql(List<Long> regionIds, Long underpassId) {
        return new NativeSql(ACTION_SELECT)
                .andIf(regionIds != null, " AND u.region_id IN (:regionIds)", "regionIds", regionIds)
                .andIf(underpassId != null, " AND u.id = :underpassId", "underpassId", underpassId);
    }

    /** 기간 안에 쌓인 행 수 (목록은 100건까지만 내려 주므로 전체 건수는 따로 센다) */
    private int countBetween(String fromClause, String timeColumn, List<Long> regionIds, Long underpassId,
                             LocalDateTime from, LocalDateTime to) {
        NativeSql sql = new NativeSql("SELECT COUNT(*) AS total FROM " + fromClause
                + " JOIN underpass u ON u.id = e.underpass_id AND u.is_deleted = FALSE AND e.is_deleted = FALSE WHERE " + timeColumn
                + " >= :from AND " + timeColumn + " < :to")
                .param("from", from).param("to", to)
                .andIf(regionIds != null, " AND u.region_id IN (:regionIds)", "regionIds", regionIds)
                .andIf(underpassId != null, " AND u.id = :underpassId", "underpassId", underpassId);
        return (int) sql.single(em, PmQueryServiceImpl.Total.class).getTotal();
    }
}
