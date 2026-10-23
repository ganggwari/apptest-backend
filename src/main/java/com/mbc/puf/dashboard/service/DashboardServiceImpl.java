package com.mbc.puf.dashboard.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.enums.UnderpassEventType;
import com.mbc.puf.common.jpa.DbEnums;
import com.mbc.puf.common.jpa.NativeSql;
import com.mbc.puf.dashboard.dto.AlertStatsSummary;
import com.mbc.puf.dashboard.dto.ControlStatsSummary;
import com.mbc.puf.dashboard.dto.FacilityKpiSummary;
import com.mbc.puf.dashboard.dto.ManagerActivityItem;
import com.mbc.puf.dashboard.dto.RegionRiskLevelItem;
import com.mbc.puf.dashboard.dto.RegionStatusItem;
import com.mbc.puf.dashboard.dto.RiskMarginItem;
import com.mbc.puf.dashboard.dto.RiskScoreOverviewItem;
import com.mbc.puf.dashboard.dto.RiskTrendPoint;
import com.mbc.puf.dashboard.dto.StatusSummaryResponse;
import com.mbc.puf.dashboard.dto.ThresholdOverviewItem;
import com.mbc.puf.underpass.dto.ControlHistoryItem;

import jakarta.persistence.EntityManager;

/**
 * 대시보드 통계. 전부 읽기 전용 집계라 LATERAL JOIN / FILTER / 윈도 함수(LEAD) 같은 PostgreSQL 기능을 쓰는
 * 네이티브 SQL로 둔다 (JPQL로는 표현이 안 됨). 결과는 NativeSql 이 컬럼 별칭 → DTO 프로퍼티로 채운다.
 * SQL 내용은 예전 DashboardMapper.xml 과 같다 (조건부 필터는 andIf 로 붙임).
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    // 지하차도별 최신 위험점수 1건 (LATERAL JOIN, N+1 방지)
    private static final String LATEST_SCORE = """
            LEFT JOIN LATERAL (
                  SELECT rs2.score, rs2.predicted_at
                    FROM risk_score rs2
                   WHERE rs2.underpass_id = u.id
                   ORDER BY rs2.predicted_at DESC
                   LIMIT 1
                 ) rs ON TRUE
            """;

    private final EntityManager em;

    public DashboardServiceImpl(EntityManager em) {
        this.em = em;
    }

    // MP-06: 삭제되지 않은 지하차도 전체를 상태로 집계
    @Override
    @Transactional(readOnly = true)
    public StatusSummaryResponse getStatusSummary() {
        return new NativeSql("""
                SELECT COUNT(*) FILTER (WHERE status = 'NORMAL')  AS normal_count,
                       COUNT(*) FILTER (WHERE status = 'BLOCKED') AS blocked_count,
                       COUNT(*)                                   AS total_count
                  FROM underpass
                 WHERE is_deleted = FALSE
                """).single(em, StatusSummaryResponse.class);
    }

    // MP-08: 지역별 정상/차단 개수 + 평균/최대 위험점수. 지하차도가 없는 지역도 0건으로 나온다
    @Override
    @Transactional(readOnly = true)
    public List<RegionStatusItem> getRegionStatusList(List<Long> regionIds) {
        return new NativeSql("""
                SELECT r.id   AS region_id,
                       r.name AS region_name,
                       COUNT(u.id) FILTER (WHERE u.status = 'NORMAL')  AS normal_count,
                       COUNT(u.id) FILTER (WHERE u.status = 'BLOCKED') AS blocked_count,
                       COUNT(u.id)                                     AS total_count,
                       AVG(rs.score) AS avg_risk_score,
                       MAX(rs.score) AS max_risk_score
                  FROM region r
                  LEFT JOIN underpass u ON u.region_id = r.id AND u.is_deleted = FALSE
                  LEFT JOIN LATERAL (
                        SELECT rs2.score
                          FROM risk_score rs2
                         WHERE rs2.underpass_id = u.id
                         ORDER BY rs2.predicted_at DESC
                         LIMIT 1
                       ) rs ON u.id IS NOT NULL
                 WHERE r.is_deleted = FALSE
                """)
                .andIf(regionIds != null, " AND r.id IN (:regionIds)", "regionIds", regionIds)
                .append(" GROUP BY r.id, r.name ORDER BY r.name")
                .list(em, RegionStatusItem.class);
    }

    // KPI 카드. 서로 독립적인 스칼라 서브쿼리 7개. regionIds/underpassId 는 각 서브쿼리에 같이 걸린다.
    // threshold.level 은 DB에 한글 라벨로 저장돼 있다. 점수 기준 최고 단계는 '주의'(CAUTION)라서 그 임계치를 기준으로 센다(경계·심각은 수위로만 정해짐)
    @Override
    @Transactional(readOnly = true)
    public FacilityKpiSummary getFacilityKpiSummary(List<Long> regionIds, Long underpassId, int nearMargin) {
        String scope = scopeFilter(regionIds, underpassId);
        NativeSql sql = new NativeSql("SELECT"
                + " (SELECT COUNT(*) FROM underpass u WHERE u.is_deleted = FALSE" + scope + ") AS facility_count,"
                + " (SELECT COUNT(*) FROM underpass u WHERE u.is_deleted = FALSE AND u.status = 'BLOCKED'" + scope + ") AS blocked_count,"
                + " (SELECT COUNT(*) FROM plate_log p JOIN camera c ON c.id = p.camera_id"
                + "    JOIN underpass u ON u.id = c.underpass_id"
                + "   WHERE p.review_status = 'PENDING'" + scope + ") AS pending_review_count,"
                + " (SELECT COUNT(*) FROM transit t JOIN underpass u ON u.id = t.underpass_id"
                + "   WHERE t.status = 'REMAINING'" + scope + ") AS remaining_count,"
                + " (SELECT COUNT(*) FROM alert a JOIN event e ON e.id = a.event_id"
                + "    JOIN underpass u ON u.id = e.underpass_id"
                + "   WHERE a.created_at >= date_trunc('day', NOW())" + scope + ") AS alerts_today,"
                + " (SELECT COUNT(*) FROM underpass u JOIN threshold t ON t.underpass_id = u.id AND t.level = '주의' "
                + LATEST_SCORE
                + "   WHERE u.is_deleted = FALSE AND rs.score IS NOT NULL AND rs.score >= t.min_score" + scope
                + " ) AS danger_level_count,"
                + " (SELECT COUNT(*) FROM underpass u JOIN threshold t ON t.underpass_id = u.id AND t.level = '주의' "
                + LATEST_SCORE
                + "   WHERE u.is_deleted = FALSE AND rs.score IS NOT NULL AND rs.score < t.min_score"
                + "     AND (t.min_score - rs.score) <= :nearMargin" + scope
                + " ) AS near_danger_count")
                .param("nearMargin", nearMargin);
        bindScope(sql, regionIds, underpassId);
        return sql.single(em, FacilityKpiSummary.class);
    }

    // 지하차도별 최신 위험점수 1건씩. 예측 이력이 없는 지하차도는 점수/시각이 null
    @Override
    @Transactional(readOnly = true)
    public List<RiskScoreOverviewItem> getLatestRiskScores(List<Long> regionIds) {
        return new NativeSql("SELECT u.id AS underpass_id, u.name AS underpass_name,"
                + " rs.score AS risk_score, rs.predicted_at FROM underpass u " + LATEST_SCORE
                + " WHERE u.is_deleted = FALSE")
                .andIf(regionIds != null, " AND u.region_id IN (:regionIds)", "regionIds", regionIds)
                .append(" ORDER BY u.name")
                .list(em, RiskScoreOverviewItem.class);
    }

    // 지역(또는 전국) 범위의 통합 타임라인: 이벤트 + 전광판 조작 + 알림 발송, 최신순 limit개
    @Override
    @Transactional(readOnly = true)
    public List<ControlHistoryItem> getTimeline(List<Long> regionIds, int limit) {
        String region = regionIds != null ? " AND u.region_id IN (:regionIds)" : "";
        NativeSql sql = new NativeSql("""
                SELECT 'EVENT' AS kind, e.underpass_id, u.name, CAST(e.type AS text), CAST(e.risk_level AS text),
                       e.score, e.reason, CAST(NULL AS text), e.operator_id, e.created_at
                  FROM event e JOIN underpass u ON u.id = e.underpass_id
                 WHERE u.is_deleted = FALSE""" + region + """

                UNION ALL
                SELECT 'DISPLAY', dh.underpass_id, u.name, NULL, NULL, NULL, NULL, dh.new_content,
                       dh.changed_by, dh.changed_at
                  FROM display_history dh
                  JOIN underpass u ON u.id = dh.underpass_id
                 WHERE u.is_deleted = FALSE""" + region + """

                UNION ALL
                SELECT 'ALERT', ue.underpass_id, u.name, NULL, NULL, NULL, NULL, NULL, NULL, a.created_at
                  FROM alert a JOIN event ue ON ue.id = a.event_id JOIN underpass u ON u.id = ue.underpass_id
                 WHERE u.is_deleted = FALSE""" + region + """

                 ORDER BY 10 DESC
                 LIMIT :limit
                """).param("limit", limit);
        if (regionIds != null) {
            sql.param("regionIds", regionIds);
        }
        return sql.rows(em).stream().map(r -> new ControlHistoryItem(
                (String) r[0],
                ((Number) r[1]).longValue(),
                (String) r[2],
                DbEnums.fromDbValue(UnderpassEventType.class, (String) r[3]),
                DbEnums.fromDbValue(RiskLevel.class, (String) r[4]),
                (BigDecimal) r[5],
                (String) r[6],
                (String) r[7],
                r[8] == null ? null : ((Number) r[8]).longValue(),
                toLocalDateTime(r[9]))).toList();
    }

    // "전체 임계치 현황표": 여러 지하차도의 임계치를 지역명과 함께
    @Override
    @Transactional(readOnly = true)
    public List<ThresholdOverviewItem> getThresholdOverview(List<Long> regionIds) {
        return new NativeSql("""
                SELECT u.id AS underpass_id, u.name AS underpass_name, r.name AS region_name,
                       CAST(t.level AS text) AS level, t.min_score AS risk_score_min, t.updated_at
                  FROM threshold t
                  JOIN underpass u ON u.id = t.underpass_id
                  LEFT JOIN region r ON r.id = u.region_id
                 WHERE u.is_deleted = FALSE
                """)
                .andIf(regionIds != null, " AND u.region_id IN (:regionIds)", "regionIds", regionIds)
                .append(" ORDER BY u.name, t.min_score ASC")
                .list(em, ThresholdOverviewItem.class);
    }

    // "위험 임박 TOP": 주의(점수 기준 최고 단계) 임계치까지 남은 점수(margin) 오름차순 (0 이하 = 이미 초과, 가장 급한 것부터)
    @Override
    @Transactional(readOnly = true)
    public List<RiskMarginItem> getRiskMarginList(List<Long> regionIds, int limit) {
        return new NativeSql("SELECT u.id AS underpass_id, u.name AS underpass_name, r.name AS region_name,"
                + " rs.score AS risk_score, t.min_score AS danger_threshold, (t.min_score - rs.score) AS margin"
                + " FROM underpass u LEFT JOIN region r ON r.id = u.region_id"
                + " JOIN threshold t ON t.underpass_id = u.id AND t.level = '주의' " + LATEST_SCORE
                + " WHERE u.is_deleted = FALSE AND rs.score IS NOT NULL")
                .andIf(regionIds != null, " AND u.region_id IN (:regionIds)", "regionIds", regionIds)
                .append(" ORDER BY margin ASC LIMIT :limit")
                .param("limit", limit)
                .list(em, RiskMarginItem.class);
    }

    // 위험도 추이: 최근 days일, 일자별 평균/최대 위험점수
    @Override
    @Transactional(readOnly = true)
    public List<RiskTrendPoint> getRiskTrend(List<Long> regionIds, Long underpassId, int days) {
        NativeSql sql = new NativeSql("""
                SELECT CAST(date_trunc('day', rs.predicted_at) AS date) AS trend_date,
                       AVG(rs.score) AS avg_risk_score,
                       MAX(rs.score) AS max_risk_score
                  FROM risk_score rs
                  JOIN underpass u ON u.id = rs.underpass_id
                 WHERE u.is_deleted = FALSE
                   AND rs.predicted_at >= NOW() - make_interval(days => :days)
                """ + scopeFilter(regionIds, underpassId) + " GROUP BY trend_date ORDER BY trend_date")
                .param("days", days);
        bindScope(sql, regionIds, underpassId);
        return sql.list(em, RiskTrendPoint.class);
    }

    // 시·도별 5단계(안전/관심/주의/경계/심각) 시설 수 (현재 위험단계 기준 스냅샷, 한글 DB값 기준 집계)
    @Override
    @Transactional(readOnly = true)
    public List<RegionRiskLevelItem> getRegionRiskLevel(List<Long> regionIds) {
        return new NativeSql("""
                SELECT r.id   AS region_id,
                       r.name AS region_name,
                       COUNT(u.id) FILTER (WHERE u.risk_level = '안전') AS safe_count,
                       COUNT(u.id) FILTER (WHERE u.risk_level = '관심') AS watch_count,
                       COUNT(u.id) FILTER (WHERE u.risk_level = '주의') AS caution_count,
                       COUNT(u.id) FILTER (WHERE u.risk_level = '경계') AS alert_count,
                       COUNT(u.id) FILTER (WHERE u.risk_level = '심각') AS severe_count,
                       COUNT(u.id)                                      AS total_count
                  FROM region r
                  LEFT JOIN underpass u ON u.region_id = r.id AND u.is_deleted = FALSE
                 WHERE r.is_deleted = FALSE
                """)
                .andIf(regionIds != null, " AND r.id IN (:regionIds)", "regionIds", regionIds)
                .append(" GROUP BY r.id, r.name ORDER BY r.name")
                .list(em, RegionRiskLevelItem.class);
    }

    // 통제 통계: 기간 내 차단/해제 건수 + 바로 이어진 BLOCK→RELEASE 쌍의 지속시간(분).
    // LEAD()로 지하차도별 시간순 다음 이벤트를 찾아 BLOCK 다음이 RELEASE인 쌍만 집계 (끊긴 쌍은 자연히 제외)
    @Override
    @Transactional(readOnly = true)
    public ControlStatsSummary getControlStats(List<Long> regionIds, Long underpassId, int days) {
        NativeSql sql = new NativeSql("""
                WITH ordered AS (
                    SELECT e.underpass_id, e.type, e.created_at,
                           LEAD(e.type) OVER (PARTITION BY e.underpass_id ORDER BY e.created_at, e.id) AS next_type,
                           LEAD(e.created_at) OVER (PARTITION BY e.underpass_id ORDER BY e.created_at, e.id) AS next_at
                      FROM event e
                      JOIN underpass u ON u.id = e.underpass_id
                     WHERE u.is_deleted = FALSE
                       AND e.type IN ('BLOCK', 'RELEASE')
                       AND e.created_at >= NOW() - make_interval(days => :days)
                """ + scopeFilter(regionIds, underpassId) + """

                )
                SELECT COUNT(*) FILTER (WHERE type = 'BLOCK')   AS block_count,
                       COUNT(*) FILTER (WHERE type = 'RELEASE') AS release_count,
                       CAST(SUM(EXTRACT(EPOCH FROM (next_at - created_at)))
                            FILTER (WHERE type = 'BLOCK' AND next_type = 'RELEASE') / 60 AS bigint) AS total_duration_minutes,
                       CAST(AVG(EXTRACT(EPOCH FROM (next_at - created_at)))
                            FILTER (WHERE type = 'BLOCK' AND next_type = 'RELEASE') / 60 AS bigint) AS avg_duration_minutes
                  FROM ordered
                """).param("days", days);
        bindScope(sql, regionIds, underpassId);
        return sql.single(em, ControlStatsSummary.class);
    }

    // 알림 발송 통계: 기간 내 유형별(PREDICTIVE/FINAL) 건수
    @Override
    @Transactional(readOnly = true)
    public AlertStatsSummary getAlertStats(List<Long> regionIds, Long underpassId, int days) {
        NativeSql sql = new NativeSql("""
                SELECT COUNT(*) AS total_count,
                       COUNT(*) FILTER (WHERE a.type = 'PREDICTIVE') AS predictive_count,
                       COUNT(*) FILTER (WHERE a.type = 'FINAL')      AS roi_final_count
                  FROM alert a
                  JOIN event e ON e.id = a.event_id
                  JOIN underpass u ON u.id = e.underpass_id
                 WHERE u.is_deleted = FALSE
                   AND a.created_at >= NOW() - make_interval(days => :days)
                """ + scopeFilter(regionIds, underpassId)).param("days", days);
        bindScope(sql, regionIds, underpassId);
        return sql.single(em, AlertStatsSummary.class);
    }

    // 관리자별 활동 건수(기간 내 차단·해제·전광판 문구 변경 합계). 활동이 없는 관리자는 빠진다
    @Override
    @Transactional(readOnly = true)
    public List<ManagerActivityItem> getManagerActivity(List<Long> regionIds, int days) {
        String region = regionIds != null ? " AND u.region_id IN (:regionIds)" : "";
        NativeSql sql = new NativeSql("""
                SELECT m.id AS manager_id, emp.name AS manager_name,
                       COALESCE(ev.block_count, 0)   AS block_count,
                       COALESCE(ev.release_count, 0) AS release_count,
                       COALESCE(dl.display_count, 0) AS display_log_count,
                       COALESCE(ev.block_count, 0) + COALESCE(ev.release_count, 0) + COALESCE(dl.display_count, 0) AS total_count
                  FROM manager m
                  JOIN employee emp ON emp.id = m.employee_id
                  LEFT JOIN (
                        SELECT e.operator_id,
                               COUNT(*) FILTER (WHERE e.type = 'BLOCK')   AS block_count,
                               COUNT(*) FILTER (WHERE e.type = 'RELEASE') AS release_count
                          FROM event e
                          JOIN underpass u ON u.id = e.underpass_id
                         WHERE e.operator_id IS NOT NULL
                           AND e.type IN ('BLOCK', 'RELEASE')
                           AND e.created_at >= NOW() - make_interval(days => :days)""" + region + """

                         GROUP BY e.operator_id
                       ) ev ON ev.operator_id = m.id
                  LEFT JOIN (
                        SELECT dh.changed_by, COUNT(*) AS display_count
                          FROM display_history dh
                          JOIN underpass u ON u.id = dh.underpass_id
                         WHERE dh.changed_at >= NOW() - make_interval(days => :days)""" + region + """

                         GROUP BY dh.changed_by
                       ) dl ON dl.changed_by = m.id
                 WHERE m.status <> 'WITHDRAWN'
                   AND (ev.block_count IS NOT NULL OR dl.display_count IS NOT NULL)
                 ORDER BY total_count DESC
                """).param("days", days);
        if (regionIds != null) {
            sql.param("regionIds", regionIds);
        }
        return sql.list(em, ManagerActivityItem.class);
    }

    // ---- 내부 메서드 ----

    // 지역/시설 범위 조건 (값이 있을 때만). 별칭 u = underpass 기준
    private static String scopeFilter(List<Long> regionIds, Long underpassId) {
        return (regionIds != null ? " AND u.region_id IN (:regionIds)" : "")
                + (underpassId != null ? " AND u.id = :underpassId" : "");
    }

    private static void bindScope(NativeSql sql, List<Long> regionIds, Long underpassId) {
        if (regionIds != null) {
            sql.param("regionIds", regionIds);
        }
        if (underpassId != null) {
            sql.param("underpassId", underpassId);
        }
    }

    private static java.time.LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        return (value instanceof java.sql.Timestamp ts) ? ts.toLocalDateTime() : (java.time.LocalDateTime) value;
    }
}