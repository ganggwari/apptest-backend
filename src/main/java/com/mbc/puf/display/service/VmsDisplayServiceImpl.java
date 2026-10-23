package com.mbc.puf.display.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.jpa.DbEnums;
import com.mbc.puf.display.dto.VmsPageItem;
import com.mbc.puf.display.service.VmsTextBuilder.Kind;
import com.mbc.puf.stream.StreamPayloads;
import com.mbc.puf.stream.StreamPublisher;

import jakarta.persistence.EntityManager;

@Service
public class VmsDisplayServiceImpl implements VmsDisplayService {

    private static final int TEMP_PAGE_OFFSET = 100_000;   // page_no 순서를 바꿀 때 UNIQUE 충돌을 피하려고 잠깐 얹는 값

    private final EntityManager em;
    private final DbClock dbClock;
    private final StreamPublisher streamPublisher;

    private final double nearM;            // 이 거리 이하는 근접 전광판 (통제일 때 "통행 금지")
    private final int releaseNoticeMin;    // 해제 안내를 보여주는 시간(분)
    private final RiskLevel minLevel;      // 이 위험단계부터 표출
    private final String defaultText;      // 보여줄 페이지가 하나도 없을 때 응답하는 기본 문구(DB 에는 저장하지 않는다)

    public VmsDisplayServiceImpl(EntityManager em, DbClock dbClock, StreamPublisher streamPublisher,
                                 @Value("${puf.display.near-m:300}") double nearM,
                                 @Value("${puf.display.release-notice-minutes:10}") int releaseNoticeMin,
                                 @Value("${puf.display.min-level:WATCH}") String minLevel,
                                 @Value("${puf.display.default-text:안전운전\n하세요}") String defaultText) {
        this.em = em;
        this.dbClock = dbClock;
        this.streamPublisher = streamPublisher;
        this.nearM = nearM;
        this.releaseNoticeMin = releaseNoticeMin;
        this.minLevel = RiskLevel.valueOf(minLevel.trim().toUpperCase());
        this.defaultText = defaultText.replace("\\n", "\n");
    }

    // ───────────────────────── 다시 맞추기 ─────────────────────────

    @Override
    @Transactional
    public void reconcileForUnderpass(Long underpassId, Long eventId, Long operatorId) {
        // 연결된 전광판 + (연결은 사라졌는데 페이지가 남은) 전광판
        List<?> ids = em.createNativeQuery("SELECT its_vms_id FROM vms_underpass WHERE underpass_id = :u"
                        + " UNION SELECT its_vms_id FROM display_page WHERE underpass_id = :u AND is_manual = FALSE")
                .setParameter("u", underpassId).getResultList();
        for (Object id : ids) {
            reconcileVms(((Number) id).longValue(), eventId, operatorId, underpassId);
        }
    }

    @Override
    @Transactional
    public boolean reconcileVms(Long vmsId, Long eventId, Long operatorId) {
        return reconcileVms(vmsId, eventId, operatorId, null);
    }

    private boolean reconcileVms(Long vmsId, Long eventId, Long operatorId, Long triggerUnderpassId) {
        List<?> specRows = em.createNativeQuery("SELECT spec FROM its_vms WHERE id = :v").setParameter("v", vmsId).getResultList();
        if (specRows.isEmpty()) {
            return false;
        }
        String spec = (String) specRows.get(0);
        LocalDateTime now = dbClock.now();

        // 1) 이 전광판에 연결된 지하차도들의 지금 상태 → 있어야 할 자동 페이지
        List<Desired> desired = desiredPages(vmsId, spec, now);

        // 2) 지금 DB 에 있는 페이지
        List<Existing> existing = existingPages(vmsId);
        List<Existing> manual = existing.stream().filter(Existing::manual).toList();
        Map<Long, Existing> autoByUnderpass = new LinkedHashMap<>();
        for (Existing e : existing) {
            if (!e.manual()) {
                autoByUnderpass.put(e.underpassId(), e);
            }
        }

        // 3) 최종 순서 = 자동(우선순위순) → 수동(기존 순서)
        List<String> wantedKeys = new ArrayList<>();
        for (Desired d : desired) {
            wantedKeys.add("A" + d.underpassId() + "|" + d.content());
        }
        for (Existing m : manual) {
            wantedKeys.add("M" + m.id() + "|" + m.content());
        }
        List<String> haveKeys = existing.stream()
                .map(e -> (e.manual() ? "M" + e.id() : "A" + e.underpassId()) + "|" + e.content()).toList();
        if (wantedKeys.equals(haveKeys)) {
            return false;   // 바뀐 게 없다
        }

        // 4) 반영: 순서를 바꾸기 전에 기존 번호를 뒤로 밀어 UNIQUE(its_vms_id, page_no) 충돌을 피한다
        em.createNativeQuery("UPDATE display_page SET page_no = page_no + :o WHERE its_vms_id = :v")
                .setParameter("o", TEMP_PAGE_OFFSET).setParameter("v", vmsId).executeUpdate();
        Set<Long> keep = desired.stream().map(Desired::underpassId).collect(Collectors.toSet());
        for (Existing e : autoByUnderpass.values()) {
            if (!keep.contains(e.underpassId())) {
                em.createNativeQuery("DELETE FROM display_page WHERE id = :id").setParameter("id", e.id()).executeUpdate();
            }
        }

        int pageNo = 1;
        for (Desired d : desired) {
            Existing old = autoByUnderpass.get(d.underpassId());
            if (old == null) {
                Object id = em.createNativeQuery("INSERT INTO display_page (its_vms_id, underpass_id, page_no, content, updated_by, is_manual)"
                                + " VALUES (:v, :u, :n, :c, :m, FALSE) RETURNING id")
                        .setParameter("v", vmsId).setParameter("u", d.underpassId()).setParameter("n", pageNo)
                        .setParameter("c", d.content()).setParameter("m", operatorId).getSingleResult();
                writeHistory(((Number) id).longValue(), vmsId, d.underpassId(), eventId, null, d.content(), operatorId);
            } else if (!old.content().equals(d.content())) {
                em.createNativeQuery("UPDATE display_page SET page_no = :n, content = :c, preset_id = NULL, updated_by = :m,"
                                + " updated_at = LOCALTIMESTAMP WHERE id = :id")
                        .setParameter("n", pageNo).setParameter("c", d.content()).setParameter("m", operatorId)
                        .setParameter("id", old.id()).executeUpdate();
                writeHistory(old.id(), vmsId, d.underpassId(), eventId, old.content(), d.content(), operatorId);
            } else {
                em.createNativeQuery("UPDATE display_page SET page_no = :n WHERE id = :id")
                        .setParameter("n", pageNo).setParameter("id", old.id()).executeUpdate();
            }
            pageNo++;
        }
        for (Existing m : manual) {
            em.createNativeQuery("UPDATE display_page SET page_no = :n WHERE id = :id")
                    .setParameter("n", pageNo++).setParameter("id", m.id()).executeUpdate();
        }

        streamPublisher.publishToAdmins(null, "display-change",
                new StreamPayloads.DisplayChange(vmsId, triggerUnderpassId, null, "SYNC"));
        streamPublisher.dataChangedPublic(null, "its-vms", "UPDATE", vmsId);
        return true;
    }

    // 전광판에 연결된 지하차도마다 페이지 종류를 정하고 문구를 만든 뒤, 우선순위(통제 > 주의 > 관심 > 해제) → 가까운 순으로 정렬
    private List<Desired> desiredPages(Long vmsId, String spec, LocalDateTime now) {
        List<?> rows = em.createNativeQuery("SELECT u.id, u.name, CAST(u.status AS text), CAST(u.risk_level AS text), vu.dist"
                        + " FROM vms_underpass vu JOIN underpass u ON u.id = vu.underpass_id"
                        + " WHERE vu.its_vms_id = :v AND u.is_deleted = FALSE")
                .setParameter("v", vmsId).getResultList();
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> underpassIds = new ArrayList<>();
        for (Object row : rows) {
            underpassIds.add(((Number) ((Object[]) row)[0]).longValue());
        }
        Map<Long, LocalDateTime> released = lastReleased(underpassIds);

        List<Desired> list = new ArrayList<>();
        for (Object row : rows) {
            Object[] r = (Object[]) row;
            Long uid = ((Number) r[0]).longValue();
            boolean blocked = "BLOCKED".equals(r[2]);
            RiskLevel level = DbEnums.fromDbValue(RiskLevel.class, (String) r[3]);
            int dist = ((Number) r[4]).intValue();
            Kind kind = VmsTextBuilder.kindOf(blocked, level, released.get(uid), now, releaseNoticeMin, minLevel);
            if (kind == null) {
                continue;
            }
            String content = VmsTextBuilder.build((String) r[1], spec, kind, dist <= nearM);
            list.add(new Desired(uid, kind, content, dist));
        }
        list.sort(Comparator.comparing((Desired d) -> d.kind().ordinal()).thenComparingInt(Desired::dist)
                .thenComparingLong(Desired::underpassId));
        return list;
    }

    private Map<Long, LocalDateTime> lastReleased(Collection<Long> underpassIds) {
        Map<Long, LocalDateTime> map = new HashMap<>();
        List<?> rows = em.createNativeQuery("SELECT underpass_id, MAX(created_at) FROM event"
                        + " WHERE CAST(type AS text) = 'RELEASE' AND underpass_id IN (:ids) GROUP BY underpass_id")
                .setParameter("ids", underpassIds).getResultList();
        for (Object row : rows) {
            Object[] r = (Object[]) row;
            map.put(((Number) r[0]).longValue(), toLocalDateTime(r[1]));
        }
        return map;
    }

    private List<Existing> existingPages(Long vmsId) {
        List<?> rows = em.createNativeQuery("SELECT id, underpass_id, page_no, content, is_manual FROM display_page"
                        + " WHERE its_vms_id = :v ORDER BY page_no").setParameter("v", vmsId).getResultList();
        List<Existing> list = new ArrayList<>();
        for (Object row : rows) {
            Object[] r = (Object[]) row;
            list.add(new Existing(((Number) r[0]).longValue(), ((Number) r[1]).longValue(), ((Number) r[2]).intValue(),
                    (String) r[3], (Boolean) r[4]));
        }
        return list;
    }

    // 이력은 지하차도 하나당 "가장 가까운 전광판"의 변경만 남긴다 (연결된 전광판이 여러 개라 전부 남기면 타임라인이 같은 내용으로 도배된다)
    private void writeHistory(Long pageId, Long vmsId, Long underpassId, Long eventId, String oldContent, String newContent,
                              Long operatorId) {
        List<?> nearest = em.createNativeQuery("SELECT its_vms_id FROM vms_underpass WHERE underpass_id = :u"
                        + " ORDER BY dist, its_vms_id").setParameter("u", underpassId).setMaxResults(1).getResultList();
        if (nearest.isEmpty() || ((Number) nearest.get(0)).longValue() != vmsId) {
            return;
        }
        em.createNativeQuery("INSERT INTO display_history (page_id, its_vms_id, underpass_id, event_id, old_content, new_content, changed_by)"
                        + " VALUES (:p, :v, :u, :e, :o, :n, :m)")
                .setParameter("p", pageId).setParameter("v", vmsId).setParameter("u", underpassId)
                .setParameter("e", eventId).setParameter("o", oldContent).setParameter("n", newContent)
                .setParameter("m", operatorId).executeUpdate();
    }

    // ───────────────────────── 주기 점검 대상 ─────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<Long> targetVmsIds() {
        List<?> rows = em.createNativeQuery("SELECT its_vms_id FROM display_page WHERE is_manual = FALSE"
                        + " UNION SELECT vu.its_vms_id FROM vms_underpass vu JOIN underpass u ON u.id = vu.underpass_id"
                        + " WHERE u.is_deleted = FALSE AND (CAST(u.status AS text) <> 'NORMAL' OR CAST(u.risk_level AS text) <> '안전')")
                .getResultList();
        return rows.stream().map(o -> ((Number) o).longValue()).toList();
    }

    // ───────────────────────── 조회 ─────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<VmsPageItem>> pagesOf(Collection<Long> vmsIds) {
        boolean all = vmsIds == null;
        if (!all && vmsIds.isEmpty()) {
            return Map.of();
        }
        var q = em.createNativeQuery("SELECT dp.id, dp.its_vms_id, dp.page_no, dp.underpass_id, u.name, dp.content, dp.is_manual,"
                + " dp.updated_by, dp.updated_at, CAST(u.status AS text), CAST(u.risk_level AS text)"
                + " FROM display_page dp JOIN underpass u ON u.id = dp.underpass_id"
                + (all ? "" : " WHERE dp.its_vms_id IN (:ids)")
                + " ORDER BY dp.its_vms_id, dp.page_no");
        if (!all) {
            q.setParameter("ids", vmsIds);
        }
        List<?> rows = q.getResultList();

        Set<Long> underpassIds = rows.stream().map(o -> ((Number) ((Object[]) o)[3]).longValue()).collect(Collectors.toSet());
        Map<Long, LocalDateTime> released = underpassIds.isEmpty() ? Map.of() : lastReleased(underpassIds);
        LocalDateTime now = dbClock.now();

        Map<Long, List<VmsPageItem>> map = new LinkedHashMap<>();
        for (Object row : rows) {
            Object[] r = (Object[]) row;
            Long uid = ((Number) r[3]).longValue();
            boolean manual = (Boolean) r[6];
            String color = "YELLOW";
            if (!manual) {
                Kind kind = VmsTextBuilder.kindOf("BLOCKED".equals(r[9]), DbEnums.fromDbValue(RiskLevel.class, (String) r[10]),
                        released.get(uid), now, releaseNoticeMin, minLevel);
                color = kind != null ? kind.color() : "YELLOW";
            }
            String content = (String) r[5];
            map.computeIfAbsent(((Number) r[1]).longValue(), k -> new ArrayList<>()).add(new VmsPageItem(
                    ((Number) r[0]).longValue(), ((Number) r[1]).longValue(), ((Number) r[2]).intValue(), uid, (String) r[4],
                    content, Arrays.asList(content.split("\n")), color, manual, false,
                    r[7] == null ? null : ((Number) r[7]).longValue(), toLocalDateTime(r[8])));
        }
        return map;
    }

    @Override
    public VmsPageItem defaultPage(Long vmsId) {
        return new VmsPageItem(null, vmsId, 1, null, null, defaultText, Arrays.asList(defaultText.split("\n")), "YELLOW",
                false, true, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public String nearestFirstPageText(Long underpassId) {
        List<?> rows = em.createNativeQuery("SELECT dp.content FROM vms_underpass vu"
                        + " JOIN display_page dp ON dp.its_vms_id = vu.its_vms_id AND dp.underpass_id = vu.underpass_id"
                        + " WHERE vu.underpass_id = :u ORDER BY vu.dist, vu.its_vms_id, dp.page_no")
                .setParameter("u", underpassId).setMaxResults(1).getResultList();
        return rows.isEmpty() ? null : ((String) rows.get(0)).replace("\n", " ");
    }

    // ───────────────────────── 보조 ─────────────────────────

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return (LocalDateTime) value;
    }

    private record Desired(Long underpassId, Kind kind, String content, int dist) {
    }

    private record Existing(Long id, Long underpassId, int pageNo, String content, boolean manual) {
    }
}
