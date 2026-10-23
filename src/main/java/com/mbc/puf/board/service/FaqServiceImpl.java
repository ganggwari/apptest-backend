package com.mbc.puf.board.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.board.dto.FaqCreateRequest;
import com.mbc.puf.board.dto.FaqDetailResponse;
import com.mbc.puf.board.dto.FaqListItem;
import com.mbc.puf.board.dto.FaqOrderRequest;
import com.mbc.puf.board.dto.FaqUpdateRequest;
import java.util.List;

import com.mbc.puf.board.entity.Faq;
import com.mbc.puf.board.repository.FaqRepository;
import com.mbc.puf.common.enums.FaqCategory;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.query.PagedQuery;
import com.mbc.puf.common.query.QueryConditions;
import com.mbc.puf.common.request.PageParams;
import com.mbc.puf.common.response.PageResponse;

import jakarta.persistence.EntityManager;

@Service
public class FaqServiceImpl implements FaqService {

    private static final String FROM = " from Faq f join Manager m on m.id = f.authorId"
            + " join Employee e on e.id = m.employeeId";

    private final FaqRepository faqRepository;
    private final DbClock dbClock;
    private final EntityManager em;
    private final com.mbc.puf.stream.StreamPublisher streamPublisher;   // SSE 실시간 이벤트

    public FaqServiceImpl(FaqRepository faqRepository, DbClock dbClock, EntityManager em,
                          com.mbc.puf.stream.StreamPublisher streamPublisher) {
        this.streamPublisher = streamPublisher;
        this.faqRepository = faqRepository;
        this.dbClock = dbClock;
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FaqListItem> getFaqList(String keyword, FaqCategory category, int page, int size) {
        PageParams pageParams = PageParams.of(page, size);
        QueryConditions c = new QueryConditions()
                .add("f.deleted = false")
                .eqIfPresent("f.category", category)                       // null 이면 전체 분류
                .containsIfPresent(keyword, "f.question", "f.answer");   // 질문+답변 통합 검색

        return PagedQuery.fetch(em, "select f, e.name" + FROM, "select count(f)" + FROM, c,
                " order by f.sortOrder asc, f.id asc", Object[].class, row -> {
                    Faq f = (Faq) row[0];
                    FaqListItem item = new FaqListItem();
                    item.setId(f.getId());
                    item.setCategory(f.getCategory());
                    item.setSortOrder(f.getSortOrder());
                    item.setViewCount(f.getViewCount());
                    item.setQuestion(f.getQuestion());
                    item.setAuthorName((String) row[1]);
                    item.setCreatedAt(f.getCreatedAt());
                    return item;
                }, pageParams);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FaqListItem> getPopularFaqs(int limit) {
        int size = Math.min(Math.max(limit, 1), 20);
        List<Object[]> rows = em.createQuery("select f, e.name" + FROM
                        + " where f.deleted = false order by f.viewCount desc, f.sortOrder asc, f.id asc", Object[].class)
                .setMaxResults(size).getResultList();
        return rows.stream().map(row -> {
            Faq f = (Faq) row[0];
            FaqListItem item = new FaqListItem();
            item.setId(f.getId());
            item.setCategory(f.getCategory());
            item.setSortOrder(f.getSortOrder());
            item.setViewCount(f.getViewCount());
            item.setQuestion(f.getQuestion());
            item.setAuthorName((String) row[1]);
            item.setCreatedAt(f.getCreatedAt());
            return item;
        }).toList();
    }

    @Override
    @Transactional
    public FaqDetailResponse getFaq(Long id, boolean countView) {
        Faq faq = getActiveFaq(id);   // 없으면 404
        if (countView) {
            faqRepository.increaseViewCount(id);
            faq = getActiveFaq(id);   // 올라간 조회수를 다시 읽는다
        }
        return toDetail(faq);
    }

    @Override
    @Transactional
    public void reorderFaqs(FaqOrderRequest request) {
        List<Long> ids = request.getIds();
        if (ids.stream().distinct().count() != ids.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "같은 FAQ id 가 두 번 들어 있습니다.");
        }
        List<Faq> found = faqRepository.findByIdInAndDeletedFalse(ids);
        if (found.size() != ids.size()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않거나 삭제된 FAQ 가 포함되어 있습니다.");
        }
        // 보낸 FAQ 들이 원래 가지고 있던 순서 번호를 오름차순으로 모아서, 보낸 순서대로 다시 나눠준다
        List<Integer> slots = found.stream().map(Faq::getSortOrder).sorted().toList();
        java.util.Map<Long, Faq> byId = found.stream().collect(java.util.stream.Collectors.toMap(Faq::getId, f -> f));
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).setSortOrder(slots.get(i));
        }
        streamPublisher.dataChangedPublic(null, "faq", "REORDER", null);
    }

    @Override
    @Transactional
    public FaqDetailResponse createFaq(Long authorId, FaqCreateRequest request) {
        Faq faq = new Faq();
        faq.setQuestion(request.getQuestion().trim());
        faq.setAnswer(request.getAnswer());
        faq.setCategory(request.getCategory() == null ? FaqCategory.ETC : request.getCategory());
        faq.setAuthorId(authorId);
        faq.setSortOrder(faqRepository.findMaxSortOrder() + 1);   // 새 FAQ 는 맨 끝
        faqRepository.saveAndFlush(faq);
        streamPublisher.dataChangedPublic(null, "faq", "CREATE", faq.getId());
        return toDetail(faq);
    }

    @Override
    @Transactional
    public FaqDetailResponse updateFaq(Long id, FaqUpdateRequest request) {
        Faq faq = getActiveFaq(id);   // 존재/삭제 여부 확인, 없으면 404
        faq.setQuestion(request.getQuestion().trim());
        faq.setAnswer(request.getAnswer());
        faq.setCategory(request.getCategory() == null ? faq.getCategory() : request.getCategory());   // 안 보내면 기존 분류 유지
        faq.setUpdatedAt(dbClock.now());
        streamPublisher.dataChangedPublic(null, "faq", "UPDATE", faq.getId());
        return toDetail(faq);
    }

    @Override
    @Transactional
    public void deleteFaq(Long id) {
        getActiveFaq(id).setDeleted(true);
        streamPublisher.dataChangedPublic(null, "faq", "DELETE", id);
    }

    private Faq getActiveFaq(Long id) {
        return faqRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 FAQ입니다. id=" + id));
    }

    private FaqDetailResponse toDetail(Faq f) {
        FaqDetailResponse detail = new FaqDetailResponse();
        detail.setId(f.getId());
        detail.setCategory(f.getCategory());
        detail.setSortOrder(f.getSortOrder());
        detail.setViewCount(f.getViewCount());
        detail.setQuestion(f.getQuestion());
        detail.setAnswer(f.getAnswer());
        detail.setAuthorId(f.getAuthorId());
        detail.setAuthorName(BoardAuthors.managerName(em, f.getAuthorId()));
        detail.setCreatedAt(f.getCreatedAt());
        detail.setUpdatedAt(f.getUpdatedAt());
        return detail;
    }
}