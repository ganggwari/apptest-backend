package com.mbc.puf.pm.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.enums.PmEventStatus;
import com.mbc.puf.common.enums.PmLevel;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.jpa.NativeSql;
import com.mbc.puf.common.request.PageParams;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.pm.dto.PmActionItem;
import com.mbc.puf.pm.dto.PmEquipmentDetailResponse;
import com.mbc.puf.pm.dto.PmEquipmentListItem;
import com.mbc.puf.pm.dto.PmEventItem;
import com.mbc.puf.pm.dto.PmScorePoint;
import com.mbc.puf.pm.dto.PmSensorTrendResponse;
import com.mbc.puf.pm.entity.PmThreshold;
import com.mbc.puf.pm.repository.PmThresholdRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 예지보전 화면 조회. 전부 읽기만 한다.
 * 최근 점수는 설비마다 pm_score 에서 가장 최근 1건을 LATERAL 로 붙인다 (uq_pm_score_equip_time 인덱스를 탐).
 * 담당 지역은 ScopeResolver 로 정한다 (목록: 담당 지역 전체 / 단건: 담당 지역 밖이면 403).
 */
@Service
@Transactional(readOnly = true)
public class PmQueryServiceImpl implements PmQueryService {

    private static final int RECENT_ACTIONS = 5;
    private static final List<String> OPEN_STATUSES = List.of(PmEventStatus.CHECK_NEEDED.name(), PmEventStatus.IN_PROGRESS.name());

    // ---- 설비 목록·상세 공통 ----
    private static final String EQUIP_SELECT = """
            SELECT e.id AS equip_id, CAST(e.type AS text) AS type, e.name AS name, u.id AS underpass_id, u.name AS underpass_name,
                   u.region_id AS region_id, ls.score AS score, CAST(ls.level AS text) AS level,
                   ls.predicted_at AS last_received_at, ls.reasons AS reasons_json,
                   oe.id AS open_event_id, CAST(oe.status AS text) AS open_event_status,
                   (SELECT s2.score FROM pm_score s2
                     WHERE s2.equip_id = e.id AND s2.predicted_at >= ls.predicted_at - INTERVAL '7 days'
                     ORDER BY s2.predicted_at ASC LIMIT 1) AS score_week_start
            """;
    private static final String EQUIP_FROM = """
             FROM pm_equipment e
             JOIN underpass u ON u.id = e.underpass_id AND u.is_deleted = FALSE AND e.is_deleted = FALSE
             LEFT JOIN LATERAL (SELECT s.score, s.level, s.predicted_at, CAST(s.reasons AS text) AS reasons
                                  FROM pm_score s WHERE s.equip_id = e.id
                                 ORDER BY s.predicted_at DESC LIMIT 1) ls ON TRUE
             LEFT JOIN LATERAL (SELECT ev.id, ev.status FROM pm_event ev
                                 WHERE ev.equip_id = e.id AND CAST(ev.status AS text) IN (:openStatuses)
                                 ORDER BY ev.id DESC LIMIT 1) oe ON TRUE
             WHERE 1 = 1
            """;

    // ---- 점검 대기 ----
    private static final String EVENT_SELECT = """
            SELECT ev.id AS event_id, e.id AS equip_id, CAST(e.type AS text) AS type, e.name AS name, u.id AS underpass_id,
                   u.name AS underpass_name, u.region_id AS region_id, CAST(ev.trigger_type AS text) AS trigger_type, CAST(ev.level AS text) AS level, ev.score AS score,
                   CAST(ev.status AS text) AS status, ev.created_at AS created_at,
                   ls.score AS current_score, CAST(ls.level AS text) AS current_level
            """;
    private static final String EVENT_FROM = """
             FROM pm_event ev
             JOIN pm_equipment e ON e.id = ev.equip_id
             JOIN underpass u ON u.id = e.underpass_id AND u.is_deleted = FALSE AND e.is_deleted = FALSE
             LEFT JOIN LATERAL (SELECT s.score, s.level FROM pm_score s WHERE s.equip_id = e.id
                                 ORDER BY s.predicted_at DESC LIMIT 1) ls ON TRUE
             WHERE CAST(ev.status AS text) IN (:statuses)
            """;

    // ---- 점검 이력 ----
    private static final String ACTION_SELECT = """
            SELECT a.id AS action_id, ev.id AS event_id, e.id AS equip_id, CAST(e.type AS text) AS type, e.name AS name,
                   u.id AS underpass_id, u.name AS underpass_name, u.region_id AS region_id, a.content AS content,
                   a.manager_id AS manager_id, emp.name AS manager_name, a.created_at AS created_at,
                   CAST(ev.level AS text) AS event_level, ev.score AS score_before,
                   (SELECT s.score FROM pm_score s WHERE s.equip_id = e.id AND s.predicted_at > a.created_at
                     ORDER BY s.predicted_at ASC LIMIT 1) AS score_after
            """;
    private static final String ACTION_FROM = """
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
    private final PmThresholdRepository thresholdRepository;
    private final DbClock dbClock;
    private final ObjectMapper objectMapper;

    public PmQueryServiceImpl(ScopeResolver scopeResolver, PmThresholdRepository thresholdRepository, DbClock dbClock,
                              ObjectMapper objectMapper) {
        this.scopeResolver = scopeResolver;
        this.thresholdRepository = thresholdRepository;
        this.dbClock = dbClock;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ 설비 목록
    @Override
    public PageResponse<PmEquipmentListItem> getEquipments(LoginUser loginUser, ScopeQuery scope, PmEquipmentType type,
                                                           PmLevel level, PageQuery page) {
        ScopeQuery resolved = scopeResolver.resolve(loginUser, scope);
        PageParams p = page.toParams();
        NativeSql rows = equipFilters(new NativeSql(EQUIP_SELECT + EQUIP_FROM), resolved, type, level)
                .append(" ORDER BY ls.score DESC NULLS LAST, e.id LIMIT :size OFFSET :offset")
                .param("size", p.getSize()).param("offset", p.getOffset());
        NativeSql count = equipFilters(new NativeSql("SELECT COUNT(*) AS total" + EQUIP_FROM), resolved, type, level);
        List<PmEquipmentListItem> items = rows.list(em, PmEquipmentListItem.class);
        items.forEach(this::fillReasons);
        fillSparklines(items);
        return new PageResponse<>(items, p.getPage(), p.getSize(), count.single(em, Total.class).getTotal());
    }

    private NativeSql equipFilters(NativeSql sql, ScopeQuery scope, PmEquipmentType type, PmLevel level) {
        return sql.param("openStatuses", OPEN_STATUSES)
                .andIf(scope.getRegionIds() != null, " AND u.region_id IN (:regionIds)", "regionIds", scope.getRegionIds())
                .andIf(scope.getUnderpassId() != null, " AND u.id = :underpassId", "underpassId", scope.getUnderpassId())
                .andIf(type != null, " AND CAST(e.type AS text) = :type", "type", type == null ? null : type.name())
                .andIf(level != null, " AND CAST(ls.level AS text) = :level", "level", level == null ? null : level.getDbValue());
    }

    // ------------------------------------------------------------------ 설비 상세
    @Override
    public PmEquipmentDetailResponse getEquipment(LoginUser loginUser, Long equipId) {
        PmEquipmentListItem item = findEquipment(loginUser, equipId);

        DetailRow row = new NativeSql("SELECT CAST(s.detail AS text) AS detail_json, s.model_version AS model_version,"
                + " s.predicted_at AS predicted_at, s.score AS score FROM pm_score s"
                + " WHERE s.equip_id = :equipId ORDER BY s.predicted_at DESC LIMIT 1")
                .param("equipId", equipId).single(em, DetailRow.class);
        String detailJson = row == null ? null : row.getDetailJson();
        JsonNode detail = detailJson == null ? null : objectMapper.readTree(detailJson);
        String anomalyStartedAt = detail != null && detail.hasNonNull("anomalyStartedAt")
                ? detail.get("anomalyStartedAt").asString() : null;

        BigDecimal cautionMin = null, dangerMin = null;
        for (PmThreshold t : thresholdRepository.findByEquipIdOrderByMinScoreAsc(equipId)) {
            if (t.getLevel() == PmLevel.CAUTION) cautionMin = t.getMinScore();
            if (t.getLevel() == PmLevel.DANGER) dangerMin = t.getMinScore();
        }

        List<PmActionItem> recent = new NativeSql(ACTION_SELECT + ACTION_FROM + " AND e.id = :equipId"
                + " ORDER BY a.created_at DESC LIMIT " + RECENT_ACTIONS).param("equipId", equipId).list(em, PmActionItem.class);
        LocalDateTime lastCheckedAt = recent.isEmpty() ? null : recent.get(0).getCreatedAt();
        return new PmEquipmentDetailResponse(item, detail, cautionMin, dangerMin, lastCheckedAt, recent,
                row == null ? null : row.getModelVersion(), row == null ? null : row.getPredictedAt(),
                anomalyStartedAt, topCauses(detail, row == null ? null : row.getScore()));
    }

    /** detail.features 에서 영향도 상위 3개를 골라 기여 점수(영향도 × 점수 / 100)로 바꾼다. features 가 없으면 빈 목록 */
    private List<PmEquipmentDetailResponse.PmTopCause> topCauses(JsonNode detail, BigDecimal score) {
        if (detail == null || !detail.has("features") || !detail.get("features").isArray() || score == null) {
            return List.of();
        }
        List<PmEquipmentDetailResponse.PmTopCause> all = new java.util.ArrayList<>();
        for (JsonNode f : detail.get("features")) {
            int c = f.path("contribution").asInt(0);
            if (c <= 0) continue;
            all.add(new PmEquipmentDetailResponse.PmTopCause(f.path("key").asString(""), f.path("label").asString(""),
                    f.hasNonNull("value") ? f.get("value").asDouble() : null, f.path("unit").asString(""),
                    f.path("normal").hasNonNull("median") ? f.get("normal").get("median").asDouble() : null, c,
                    Math.round(c * score.doubleValue()) / 100.0, f.path("direction").asString("")));
        }
        all.sort((a, b) -> Integer.compare(b.contribution(), a.contribution()));
        return all.size() > 3 ? all.subList(0, 3) : all;
    }

    /** 목록 한 쪽 설비들의 최근 24시간 1시간 평균 점수 (쿼리 1번) */
    private void fillSparklines(List<PmEquipmentListItem> items) {
        if (items.isEmpty()) return;
        java.util.Map<Long, List<BigDecimal>> byEquip = new java.util.HashMap<>();
        List<Object[]> rows = new NativeSql("""
                SELECT s.equip_id, date_trunc('hour', s.predicted_at) AS h, ROUND(AVG(s.score), 1) AS v
                FROM pm_score s WHERE s.equip_id IN (:ids) AND s.predicted_at >= :from
                GROUP BY 1, 2 ORDER BY 2
                """).param("ids", items.stream().map(PmEquipmentListItem::getEquipId).toList())
                .param("from", dbClock.now().minusHours(24)).rows(em);
        for (Object[] r : rows) {
            byEquip.computeIfAbsent(((Number) r[0]).longValue(), k -> new java.util.ArrayList<>()).add((BigDecimal) r[2]);
        }
        items.forEach(i -> i.setSparkline(byEquip.getOrDefault(i.getEquipId(), List.of())));
    }

    // ------------------------------------------------------------------ 원인 센서 시계열
    @Override
    public PmSensorTrendResponse getSensorTrend(LoginUser loginUser, Long equipId, String column, int days) {
        if (days < 1 || days > 30) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "days는 1 이상 30 이하여야 합니다.");
        }
        PmEquipmentListItem item = findEquipment(loginUser, equipId);
        List<String> cols = PmSensorServiceImpl.columnsOf(item.getType());
        if (cols == null || !cols.contains(column)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "이 설비에서 쓸 수 있는 센서 컬럼: " + cols);
        }
        String table = PmSensorServiceImpl.tableOf(item.getType());   // 화이트리스트 값이라 SQL 에 그대로 붙인다
        LocalDateTime from = dbClock.now().minusDays(days);
        List<PmSensorTrendResponse.Point> points = new java.util.ArrayList<>();
        for (Object[] r : new NativeSql("SELECT date_trunc('hour', s.recorded_at) AS h, AVG(s." + column + ") AS v FROM " + table
                + " s WHERE s.equip_id = :equipId AND s.recorded_at >= :from GROUP BY 1 ORDER BY 1")
                .param("equipId", equipId).param("from", from).rows(em)) {
            points.add(new PmSensorTrendResponse.Point(toLdt(r[0]), r[1] == null ? null : round3(((Number) r[1]).doubleValue())));
        }
        // 평소 범위: 이 설비의 가장 오래된 데이터부터 28일 (센서는 자동 삭제하지 않으므로 처음 28일이 기준)
        Object[] b = new NativeSql("SELECT MIN(s.recorded_at), AVG(s." + column + "), STDDEV_POP(s." + column + "), COUNT(s." + column + ")"
                + " FROM " + table + " s WHERE s.equip_id = :equipId AND s.recorded_at < (SELECT MIN(x.recorded_at) FROM " + table
                + " x WHERE x.equip_id = :equipId) + INTERVAL '28 days'").param("equipId", equipId).rows(em).get(0);
        PmSensorTrendResponse.Band band = null;
        if (b[0] != null && b[1] != null && ((Number) b[3]).longValue() >= 24) {
            double mean = ((Number) b[1]).doubleValue(), sd = b[2] == null ? 0 : ((Number) b[2]).doubleValue();
            LocalDateTime start = toLdt(b[0]);
            band = new PmSensorTrendResponse.Band(round3(mean), round3(sd), round3(mean - 2 * sd), round3(mean + 2 * sd),
                    start, start.plusDays(28));
        }
        return new PmSensorTrendResponse(column, points, band);
    }

    private static double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static LocalDateTime toLdt(Object o) {
        return o instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) o;
    }

    // ------------------------------------------------------------------ 점수 추이 (그래프)
    @Override
    public List<PmScorePoint> getScores(LoginUser loginUser, Long equipId, int days, Integer bucketMinutes) {
        if (days < 1 || days > 30) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "days는 1 이상 30 이하여야 합니다.");
        }
        int mins = bucketMinutes != null ? bucketMinutes : (days >= 2 ? 60 : 10);   // 7일이면 1시간마다 1점(168점)
        if (mins < 1 || mins > 1440) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "bucketMinutes는 1 이상 1440 이하여야 합니다.");
        }
        findEquipment(loginUser, equipId);   // 존재·담당 지역 확인
        return new NativeSql("""
                SELECT date_bin(make_interval(mins => :mins), s.predicted_at, TIMESTAMP '2000-01-01') AS at,
                       ROUND(AVG(s.score), 1) AS score, MAX(s.score) AS max_score
                FROM pm_score s
                WHERE s.equip_id = :equipId AND s.predicted_at >= :from
                GROUP BY 1 ORDER BY 1
                """).param("mins", mins).param("equipId", equipId).param("from", dbClock.now().minusDays(days))
                .list(em, PmScorePoint.class);
    }

    // ------------------------------------------------------------------ 점검 대기
    @Override
    public PageResponse<PmEventItem> getEvents(LoginUser loginUser, ScopeQuery scope, PmEventStatus status,
                                               PmEquipmentType type, PageQuery page) {
        ScopeQuery resolved = scopeResolver.resolve(loginUser, scope);
        PageParams p = page.toParams();
        List<String> statuses = status == null ? OPEN_STATUSES : List.of(status.name());   // 기본: 처리 중(확인필요·점검중)만
        NativeSql rows = eventFilters(new NativeSql(EVENT_SELECT + EVENT_FROM), resolved, statuses, type)
                .append(" ORDER BY ev.created_at DESC, ev.id DESC LIMIT :size OFFSET :offset")
                .param("size", p.getSize()).param("offset", p.getOffset());
        NativeSql count = eventFilters(new NativeSql("SELECT COUNT(*) AS total" + EVENT_FROM), resolved, statuses, type);
        return new PageResponse<>(rows.list(em, PmEventItem.class), p.getPage(), p.getSize(),
                count.single(em, Total.class).getTotal());
    }

    private NativeSql eventFilters(NativeSql sql, ScopeQuery scope, List<String> statuses, PmEquipmentType type) {
        return sql.param("statuses", statuses)
                .andIf(scope.getRegionIds() != null, " AND u.region_id IN (:regionIds)", "regionIds", scope.getRegionIds())
                .andIf(scope.getUnderpassId() != null, " AND u.id = :underpassId", "underpassId", scope.getUnderpassId())
                .andIf(type != null, " AND CAST(e.type AS text) = :type", "type", type == null ? null : type.name());
    }

    // ------------------------------------------------------------------ 점검 이력
    @Override
    public PageResponse<PmActionItem> getActions(LoginUser loginUser, ScopeQuery scope, PmEquipmentType type, Long equipId,
                                                 PeriodQuery period, PageQuery page) {
        ScopeQuery resolved = scopeResolver.resolve(loginUser, scope);
        PageParams p = page.toParams();
        NativeSql rows = actionFilters(new NativeSql(ACTION_SELECT + ACTION_FROM), resolved, type, equipId, period)
                .append(" ORDER BY a.created_at DESC, a.id DESC LIMIT :size OFFSET :offset")
                .param("size", p.getSize()).param("offset", p.getOffset());
        NativeSql count = actionFilters(new NativeSql("SELECT COUNT(*) AS total" + ACTION_FROM), resolved, type, equipId, period);
        return new PageResponse<>(rows.list(em, PmActionItem.class), p.getPage(), p.getSize(),
                count.single(em, Total.class).getTotal());
    }

    private NativeSql actionFilters(NativeSql sql, ScopeQuery scope, PmEquipmentType type, Long equipId, PeriodQuery period) {
        return sql
                .andIf(scope.getRegionIds() != null, " AND u.region_id IN (:regionIds)", "regionIds", scope.getRegionIds())
                .andIf(scope.getUnderpassId() != null, " AND u.id = :underpassId", "underpassId", scope.getUnderpassId())
                .andIf(type != null, " AND CAST(e.type AS text) = :type", "type", type == null ? null : type.name())
                .andIf(equipId != null, " AND e.id = :equipId", "equipId", equipId)
                .andIf(period.startAt() != null, " AND a.created_at >= :startAt", "startAt", period.startAt())
                .andIf(period.endAtExclusive() != null, " AND a.created_at < :endAt", "endAt", period.endAtExclusive());
    }

    // ------------------------------------------------------------------ 공통
    /** 설비 1건 (없으면 404, 담당 지역 밖이면 403) */
    private PmEquipmentListItem findEquipment(LoginUser loginUser, Long equipId) {
        List<PmEquipmentListItem> found = new NativeSql(EQUIP_SELECT + EQUIP_FROM + " AND e.id = :equipId")
                .param("openStatuses", OPEN_STATUSES).param("equipId", equipId).list(em, PmEquipmentListItem.class);
        if (found.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 예지보전 설비입니다. id=" + equipId);
        }
        PmEquipmentListItem item = found.get(0);
        scopeResolver.resolve(loginUser, ScopeQuery.of(null, item.getUnderpassId()));   // 담당 지역 밖이면 403
        fillReasons(item);
        return item;
    }

    private void fillReasons(PmEquipmentListItem item) {
        item.setReasons(item.getReasonsJson() == null ? List.of()
                : objectMapper.readValue(item.getReasonsJson(), new TypeReference<List<String>>() { }));
    }

    /** 최근 점수의 세부값(jsonb → 문자열) 한 칸 */
    public static class DetailRow {
        private String detailJson;
        private String modelVersion;
        private LocalDateTime predictedAt;
        private BigDecimal score;

        public String getDetailJson() { return detailJson; }
        public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
        public String getModelVersion() { return modelVersion; }
        public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
        public LocalDateTime getPredictedAt() { return predictedAt; }
        public void setPredictedAt(LocalDateTime predictedAt) { this.predictedAt = predictedAt; }
        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }
    }

    /** COUNT(*) 결과 한 칸 */
    public static class Total {
        private long total;

        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
    }
}
