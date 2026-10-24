package com.mbc.puf.pm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.enums.PmLevel;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.jpa.NativeSql;
import com.mbc.puf.common.request.PageParams;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.pm.dto.PmThresholdDetailResponse;
import com.mbc.puf.pm.dto.PmThresholdHistoryItem;
import com.mbc.puf.pm.dto.PmThresholdItem;
import com.mbc.puf.pm.dto.PmThresholdUpdateRequest;
import com.mbc.puf.pm.entity.PmThreshold;
import com.mbc.puf.pm.entity.PmThresholdHistory;
import com.mbc.puf.pm.repository.PmEquipmentRepository;
import com.mbc.puf.pm.repository.PmEquipmentRepository.EquipmentRow;
import com.mbc.puf.pm.repository.PmThresholdHistoryRepository;
import com.mbc.puf.pm.repository.PmThresholdRepository;
import com.mbc.puf.stream.StreamPublisher;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 예지보전 단계 기준 조회·수정.
 *
 *  - 설비마다 안전(0) / 주의 / 위험 3행이 pm_threshold 에 있고, 점수가 min_score 이상이면 그 단계다.
 *  - 수정은 주의·위험 두 값만 받는다 (안전은 0 고정). 값이 달라진 행만 고치고, 바뀔 때마다 pm_threshold_history 에 남긴다.
 *  - 이미 저장된 점수의 단계(pm_score.level)는 설비마다 가장 최근 1건만 새 기준으로 다시 판정한다.
 *    (목록·상세는 최근 점수의 단계를 보여 주므로, 이걸 안 하면 다음 점수가 올 때까지 옛 기준으로 보인다)
 *    이미 만들어진 점검 대기(pm_event)는 건드리지 않는다.
 *  - 바뀐 내용은 SSE data-changed (resource: pm-threshold)로 같은 지역 관리자 화면에 알린다.
 */
@Service
public class PmThresholdServiceImpl implements PmThresholdService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final int HISTORY_LIMIT = 20;

    private static final String ITEM_SELECT = """
            SELECT e.id AS equip_id, CAST(e.type AS text) AS type, e.name AS name, u.id AS underpass_id, u.name AS underpass_name,
                   u.region_id AS region_id,
                   (SELECT t.min_score FROM pm_threshold t WHERE t.equip_id = e.id AND CAST(t.level AS text) = '주의') AS caution_min,
                   (SELECT t.min_score FROM pm_threshold t WHERE t.equip_id = e.id AND CAST(t.level AS text) = '위험') AS danger_min,
                   (SELECT MAX(t.updated_at) FROM pm_threshold t WHERE t.equip_id = e.id) AS updated_at,
                   (SELECT emp.name FROM pm_threshold t
                      LEFT JOIN manager m ON m.id = t.updated_by
                      LEFT JOIN employee emp ON emp.id = m.employee_id
                     WHERE t.equip_id = e.id ORDER BY t.updated_at DESC, t.id DESC LIMIT 1) AS updated_by_name
            """;
    private static final String ITEM_FROM = """
             FROM pm_equipment e
             JOIN underpass u ON u.id = e.underpass_id AND u.is_deleted = FALSE AND e.is_deleted = FALSE
             WHERE 1 = 1
            """;

    @PersistenceContext
    private EntityManager em;

    private final PmEquipmentRepository equipmentRepository;
    private final PmThresholdRepository thresholdRepository;
    private final PmThresholdHistoryRepository historyRepository;
    private final ScopeResolver scopeResolver;
    private final DbClock dbClock;
    private final StreamPublisher streamPublisher;

    public PmThresholdServiceImpl(PmEquipmentRepository equipmentRepository, PmThresholdRepository thresholdRepository,
            PmThresholdHistoryRepository historyRepository, ScopeResolver scopeResolver, DbClock dbClock,
            StreamPublisher streamPublisher) {
        this.equipmentRepository = equipmentRepository;
        this.thresholdRepository = thresholdRepository;
        this.historyRepository = historyRepository;
        this.scopeResolver = scopeResolver;
        this.dbClock = dbClock;
        this.streamPublisher = streamPublisher;
    }

    // ------------------------------------------------------------------ 조회
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PmThresholdItem> getThresholds(LoginUser loginUser, ScopeQuery scope, PmEquipmentType type,
                                                       PageQuery page) {
        ScopeQuery resolved = scopeResolver.resolve(loginUser, scope);
        PageParams p = page.toParams();
        NativeSql rows = filters(new NativeSql(ITEM_SELECT + ITEM_FROM), resolved, type)
                .append(" ORDER BY u.id, e.type, e.id LIMIT :size OFFSET :offset")
                .param("size", p.getSize()).param("offset", p.getOffset());
        NativeSql count = filters(new NativeSql("SELECT COUNT(*) AS total" + ITEM_FROM), resolved, type);
        return new PageResponse<>(rows.list(em, PmThresholdItem.class), p.getPage(), p.getSize(),
                count.single(em, PmQueryServiceImpl.Total.class).getTotal());
    }

    private NativeSql filters(NativeSql sql, ScopeQuery scope, PmEquipmentType type) {
        return sql
                .andIf(scope.getRegionIds() != null, " AND u.region_id IN (:regionIds)", "regionIds", scope.getRegionIds())
                .andIf(scope.getUnderpassId() != null, " AND u.id = :underpassId", "underpassId", scope.getUnderpassId())
                .andIf(type != null, " AND CAST(e.type AS text) = :type", "type", type == null ? null : type.name());
    }

    @Override
    @Transactional(readOnly = true)
    public PmThresholdDetailResponse getThreshold(LoginUser loginUser, Long equipId) {
        EquipmentRow eq = equipmentRepository.findRowsByIds(List.of(equipId)).stream().findFirst().orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 예지보전 설비입니다. id=" + equipId));
        scopeResolver.resolve(loginUser, ScopeQuery.of(null, eq.getUnderpassId()));   // 담당 지역 밖이면 403
        return new PmThresholdDetailResponse(loadItems(List.of(equipId)).get(0), loadHistory(equipId));
    }

    // ------------------------------------------------------------------ 수정
    @Override
    @Transactional
    public List<PmThresholdItem> update(LoginUser loginUser, PmThresholdUpdateRequest request) {
        BigDecimal caution = request.getCautionMin().setScale(2, RoundingMode.HALF_UP);
        BigDecimal danger = request.getDangerMin().setScale(2, RoundingMode.HALF_UP);
        if (caution.compareTo(danger) >= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "주의 기준(cautionMin)은 위험 기준(dangerMin)보다 작아야 합니다.");
        }
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(request.getEquipIds()));

        Map<Long, EquipmentRow> equipments = equipmentRepository.findRowsByIds(ids).stream()
                .collect(Collectors.toMap(EquipmentRow::getEquipId, r -> r));
        List<Long> missing = ids.stream().filter(id -> !equipments.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 예지보전 설비입니다. id=" + missing);
        }
        for (EquipmentRow eq : equipments.values()) {
            scopeResolver.requireOwnable(loginUser, eq.getRegionId());   // 하나라도 담당 지역 밖이면 전체 거절
        }

        Map<Long, List<PmThreshold>> existing = thresholdRepository.findByEquipIdIn(ids).stream()
                .collect(Collectors.groupingBy(PmThreshold::getEquipId));
        var now = dbClock.now();
        Set<Long> changedEquips = new LinkedHashSet<>();

        for (Long equipId : ids) {
            Map<PmLevel, PmThreshold> byLevel = new LinkedHashMap<>();
            existing.getOrDefault(equipId, List.of()).forEach(t -> byLevel.put(t.getLevel(), t));
            boolean changed = false;
            changed |= apply(byLevel, equipId, PmLevel.SAFE, ZERO, loginUser, now);
            changed |= apply(byLevel, equipId, PmLevel.CAUTION, caution, loginUser, now);
            changed |= apply(byLevel, equipId, PmLevel.DANGER, danger, loginUser, now);
            if (changed) {
                changedEquips.add(equipId);
            }
        }

        if (!changedEquips.isEmpty()) {
            em.flush();
            rejudgeLatestScores(changedEquips);
            Map<Long, Long> firstEquipOfRegion = new LinkedHashMap<>();
            for (Long id : changedEquips) {
                firstEquipOfRegion.putIfAbsent(equipments.get(id).getRegionId(), id);
            }
            firstEquipOfRegion.forEach((regionId, equipId) ->
                    streamPublisher.dataChangedToAdmins(regionId, "pm-threshold", "UPDATE", equipId));
        }
        return loadItems(ids);
    }

    /** 한 단계 행을 새 값으로 맞춘다. 행이 없으면 만들고, 값이 달라졌을 때만 고친다. 바뀌었으면 true */
    private boolean apply(Map<PmLevel, PmThreshold> byLevel, Long equipId, PmLevel level, BigDecimal value,
                          LoginUser loginUser, java.time.LocalDateTime now) {
        PmThreshold row = byLevel.get(level);
        if (row == null) {
            row = new PmThreshold();
            row.setEquipId(equipId);
            row.setLevel(level);
            row.setMinScore(value);
            row.setUpdatedBy(loginUser.id());
            row = thresholdRepository.save(row);
            historyRepository.save(new PmThresholdHistory(row.getId(), null, value, loginUser.id(), now));
            return true;
        }
        if (row.getMinScore().compareTo(value) == 0) {
            return false;
        }
        BigDecimal old = row.getMinScore();
        row.setMinScore(value);
        row.setUpdatedBy(loginUser.id());
        row.setUpdatedAt(now);
        thresholdRepository.save(row);
        historyRepository.save(new PmThresholdHistory(row.getId(), old, value, loginUser.id(), now));
        return true;
    }

    /** 설비마다 가장 최근 점수 1건의 단계를 새 기준으로 다시 판정해서 고친다 */
    private void rejudgeLatestScores(Set<Long> equipIds) {
        Map<Long, List<PmThreshold>> thresholds = thresholdRepository.findByEquipIdIn(equipIds).stream()
                .collect(Collectors.groupingBy(PmThreshold::getEquipId));
        List<Object[]> latest = new NativeSql("""
                SELECT DISTINCT ON (s.equip_id) s.equip_id, s.id, s.score
                  FROM pm_score s WHERE s.equip_id IN (:ids)
                 ORDER BY s.equip_id, s.predicted_at DESC
                """).param("ids", equipIds).rows(em);
        for (Object[] row : latest) {
            Long equipId = ((Number) row[0]).longValue();
            Long scoreId = ((Number) row[1]).longValue();
            BigDecimal score = new BigDecimal(row[2].toString());
            PmLevel level = PmJudge.judge(score, thresholds.get(equipId));
            em.createNativeQuery("UPDATE pm_score SET level = CAST(:level AS pm_level) WHERE id = :id")
                    .setParameter("level", level.getDbValue()).setParameter("id", scoreId).executeUpdate();
        }
    }

    // ------------------------------------------------------------------ 공통
    private List<PmThresholdItem> loadItems(List<Long> equipIds) {
        em.flush();
        List<PmThresholdItem> rows = new NativeSql(ITEM_SELECT + ITEM_FROM + " AND e.id IN (:ids) ORDER BY e.id")
                .param("ids", equipIds).list(em, PmThresholdItem.class);
        return rows;
    }

    private List<PmThresholdHistoryItem> loadHistory(Long equipId) {
        return new NativeSql("""
                SELECT CAST(t.level AS text) AS level, h.old_min AS old_min, h.new_min AS new_min,
                       h.changed_by AS changed_by, emp.name AS changed_by_name, h.changed_at AS changed_at
                  FROM pm_threshold_history h
                  JOIN pm_threshold t ON t.id = h.threshold_id
                  LEFT JOIN manager m ON m.id = h.changed_by
                  LEFT JOIN employee emp ON emp.id = m.employee_id
                 WHERE t.equip_id = :equipId
                 ORDER BY h.changed_at DESC, h.id DESC
                 LIMIT :limit
                """).param("limit", HISTORY_LIMIT).param("equipId", equipId).list(em, PmThresholdHistoryItem.class);
    }
}
