package com.mbc.puf.display.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.DisplayCategory;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.common.jpa.NativeSql;
import com.mbc.puf.common.request.PageParams;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.display.dto.DisplayHistoryItem;
import com.mbc.puf.display.dto.DisplayPageSaveRequest;
import com.mbc.puf.display.dto.DisplayPresetItem;
import com.mbc.puf.display.dto.VmsPageItem;
import com.mbc.puf.display.entity.DisplayHistory;
import com.mbc.puf.display.entity.DisplayPage;
import com.mbc.puf.display.entity.DisplayPreset;
import com.mbc.puf.display.repository.DisplayHistoryRepository;
import com.mbc.puf.display.repository.DisplayPageRepository;
import com.mbc.puf.display.repository.DisplayPresetRepository;
import com.mbc.puf.stream.StreamPayloads;
import com.mbc.puf.stream.StreamPublisher;

import jakarta.persistence.EntityManager;

@Service
public class DisplayServiceImpl implements DisplayService {

    private final DisplayPresetRepository presetRepository;
    private final DisplayPageRepository pageRepository;
    private final DisplayHistoryRepository historyRepository;
    private final VmsDisplayService vmsDisplayService;
    private final DbClock dbClock;
    private final EntityManager em;
    private final ScopeResolver scopeResolver;
    private final StreamPublisher streamPublisher;

    public DisplayServiceImpl(DisplayPresetRepository presetRepository, DisplayPageRepository pageRepository,
                              DisplayHistoryRepository historyRepository, VmsDisplayService vmsDisplayService, DbClock dbClock,
                              EntityManager em, ScopeResolver scopeResolver, StreamPublisher streamPublisher) {
        this.presetRepository = presetRepository;
        this.pageRepository = pageRepository;
        this.historyRepository = historyRepository;
        this.vmsDisplayService = vmsDisplayService;
        this.dbClock = dbClock;
        this.em = em;
        this.scopeResolver = scopeResolver;
        this.streamPublisher = streamPublisher;
    }

    // ─── 프리셋 조회 ─────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<DisplayPresetItem> getPresets(DisplayCategory category) {
        List<DisplayPreset> presets = (category != null)
                ? presetRepository.findByCategory(category)
                : presetRepository.findAll();
        return presets.stream()
                .map(p -> new DisplayPresetItem(p.getId(), p.getCategory(), p.getContent()))
                .collect(Collectors.toList());
    }

    // ─── 페이지 목록 ─────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<VmsPageItem> getPages(Long vmsId) {
        requireVms(vmsId);
        List<VmsPageItem> pages = vmsDisplayService.pagesOf(List.of(vmsId)).get(vmsId);
        return pages == null || pages.isEmpty() ? List.of(vmsDisplayService.defaultPage(vmsId)) : pages;
    }

    // ─── 수동 페이지 생성 ────────────────────────────────────────────

    @Override
    @Transactional
    public VmsPageItem createPage(LoginUser loginUser, Long vmsId, DisplayPageSaveRequest request) {
        requireVms(vmsId);
        if (request.getUnderpassId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "원인 지하차도(underpassId)는 필수입니다.");
        }
        Long regionId = underpassRegion(request.getUnderpassId());
        scopeResolver.requireOwnable(loginUser, regionId);

        int nextPageNo = pageRepository.findMaxPageNoByVmsId(vmsId).map(max -> max + 1).orElse(1);
        DisplayPage page = new DisplayPage();
        page.setVmsId(vmsId);
        page.setUnderpassId(request.getUnderpassId());
        page.setPageNo(nextPageNo);
        page.setContent(request.getContent().trim());
        page.setPresetId(request.getPresetId());
        page.setManual(true);
        page.setUpdatedBy(loginUser.id());
        page.setUpdatedAt(dbClock.now());
        pageRepository.save(page);

        saveHistory(page, null, page.getContent(), loginUser.id());
        publish(vmsId, page.getUnderpassId(), page.getPageNo(), "CREATE");
        return toItem(page);
    }

    // ─── 수동 페이지 수정 ────────────────────────────────────────────

    @Override
    @Transactional
    public VmsPageItem updatePage(LoginUser loginUser, Long vmsId, Integer pageNo, DisplayPageSaveRequest request) {
        DisplayPage page = requireManualPage(vmsId, pageNo);
        scopeResolver.requireOwnable(loginUser, underpassRegion(page.getUnderpassId()));

        String newContent = request.getContent().trim();
        String oldContent = page.getContent();
        if (!newContent.equals(oldContent)) {   // 내용이 실제로 바뀔 때만 이력을 남긴다
            page.setContent(newContent);
            page.setPresetId(request.getPresetId());
            page.setUpdatedBy(loginUser.id());
            page.setUpdatedAt(dbClock.now());
            saveHistory(page, oldContent, newContent, loginUser.id());
            publish(vmsId, page.getUnderpassId(), pageNo, "UPDATE");
        }
        return toItem(page);
    }

    // ─── 수동 페이지 삭제 ────────────────────────────────────────────

    @Override
    @Transactional
    public void deletePage(LoginUser loginUser, Long vmsId, Integer pageNo) {
        DisplayPage page = requireManualPage(vmsId, pageNo);
        scopeResolver.requireOwnable(loginUser, underpassRegion(page.getUnderpassId()));
        Long underpassId = page.getUnderpassId();
        pageRepository.delete(page);
        pageRepository.flush();
        pageRepository.shiftPageNoDown(vmsId, pageNo);   // 삭제된 페이지 뒤의 번호를 1씩 내린다
        publish(vmsId, underpassId, pageNo, "DELETE");
    }

    // ─── 변경 이력 ───────────────────────────────────────────────────

    private static final String HISTORY_SELECT = """
            SELECT h.id AS history_id, h.its_vms_id AS vms_id, v.name AS vms_name, h.page_id AS page_id,
                   h.underpass_id AS underpass_id, u.name AS underpass_name, h.event_id AS event_id,
                   h.old_content AS old_content, h.new_content AS new_content, h.changed_by AS changed_by,
                   emp.name AS changed_by_name, h.changed_at AS changed_at
            """;
    private static final String HISTORY_FROM = """
             FROM display_history h
             JOIN its_vms v ON v.id = h.its_vms_id
             JOIN underpass u ON u.id = h.underpass_id
             LEFT JOIN manager m ON m.id = h.changed_by
             LEFT JOIN employee emp ON emp.id = m.employee_id
             WHERE 1 = 1
            """;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DisplayHistoryItem> getHistory(LoginUser loginUser, ScopeQuery scope, Long vmsId, String source,
                                                       String managerName, PeriodQuery period, PageQuery page) {
        String src = source == null || source.isBlank() ? null : source.trim().toUpperCase();
        if (src != null && !src.equals("AUTO") && !src.equals("MANUAL")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "source는 AUTO 또는 MANUAL 이어야 합니다.");
        }
        ScopeQuery resolved = scopeResolver.resolve(loginUser, scope);
        PageParams p = page.toParams();
        NativeSql rows = historyFilters(new NativeSql(HISTORY_SELECT + HISTORY_FROM), resolved, vmsId, src, managerName, period)
                .append(" ORDER BY h.changed_at DESC, h.id DESC LIMIT :size OFFSET :offset")
                .param("size", p.getSize()).param("offset", p.getOffset());
        NativeSql count = historyFilters(new NativeSql("SELECT COUNT(*) AS total" + HISTORY_FROM), resolved, vmsId, src, managerName, period);
        return new PageResponse<>(rows.list(em, DisplayHistoryItem.class), p.getPage(), p.getSize(),
                count.single(em, Total.class).getTotal());
    }

    private NativeSql historyFilters(NativeSql sql, ScopeQuery scope, Long vmsId, String src, String managerName, PeriodQuery period) {
        String nameLike = com.mbc.puf.common.util.LikePattern.lowerContainsOrNull(managerName);
        return sql
                .andIf(nameLike != null, " AND LOWER(emp.name) LIKE :nameLike ESCAPE '\\'", "nameLike", nameLike)
                .andIf(scope.getRegionIds() != null, " AND u.region_id IN (:regionIds)", "regionIds", scope.getRegionIds())
                .andIf(scope.getUnderpassId() != null, " AND u.id = :underpassId", "underpassId", scope.getUnderpassId())
                .andIf(vmsId != null, " AND h.its_vms_id = :vmsId", "vmsId", vmsId)
                .append("AUTO".equals(src) ? " AND h.changed_by IS NULL" : "MANUAL".equals(src) ? " AND h.changed_by IS NOT NULL" : "")
                .andIf(period.startAt() != null, " AND h.changed_at >= :startAt", "startAt", period.startAt())
                .andIf(period.endAtExclusive() != null, " AND h.changed_at < :endAt", "endAt", period.endAtExclusive());
    }

    /** COUNT(*) 결과 한 칸 */
    public static class Total {
        private long total;

        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
    }

    // ─── 내부 헬퍼 ──────────────────────────────────────────────────

    private void requireVms(Long vmsId) {
        Number n = (Number) em.createNativeQuery("SELECT COUNT(*) FROM its_vms WHERE id = :id")
                .setParameter("id", vmsId).getSingleResult();
        if (n.longValue() == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "전광판을 찾을 수 없습니다. vmsId=" + vmsId);
        }
    }

    private Long underpassRegion(Long underpassId) {
        List<?> rows = em.createNativeQuery("SELECT region_id FROM underpass WHERE id = :id AND is_deleted = FALSE")
                .setParameter("id", underpassId).getResultList();
        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "지하차도를 찾을 수 없습니다. underpassId=" + underpassId);
        }
        return ((Number) rows.get(0)).longValue();
    }

    private DisplayPage requireManualPage(Long vmsId, Integer pageNo) {
        DisplayPage page = pageRepository.findByVmsIdAndPageNo(vmsId, pageNo)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "전광판 페이지를 찾을 수 없습니다. vmsId=" + vmsId + ", pageNo=" + pageNo));
        if (!page.isManual()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "자동 표출 페이지는 수정·삭제할 수 없습니다. 지하차도 상태가 바뀌면 자동으로 바뀝니다.");
        }
        return page;
    }

    private void publish(Long vmsId, Long underpassId, Integer pageNo, String action) {
        streamPublisher.publishToAdmins(null, "display-change", new StreamPayloads.DisplayChange(vmsId, underpassId, pageNo, action));
        streamPublisher.dataChangedPublic(null, "its-vms", "UPDATE", vmsId);
    }

    private void saveHistory(DisplayPage page, String oldContent, String newContent, Long changedBy) {
        DisplayHistory h = new DisplayHistory();
        h.setPageId(page.getId());
        h.setVmsId(page.getVmsId());
        h.setUnderpassId(page.getUnderpassId());
        h.setOldContent(oldContent);
        h.setNewContent(newContent);
        h.setChangedBy(changedBy);
        historyRepository.save(h);
    }

    private VmsPageItem toItem(DisplayPage p) {
        return new VmsPageItem(p.getId(), p.getVmsId(), p.getPageNo(), p.getUnderpassId(), null, p.getContent(),
                Arrays.asList(p.getContent().split("\n")), "YELLOW", p.isManual(), false, p.getUpdatedBy(), p.getUpdatedAt());
    }
}
