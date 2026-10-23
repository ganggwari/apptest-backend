package com.mbc.puf.board.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.board.dto.InquiryCreateRequest;
import com.mbc.puf.board.dto.InquiryDetailResponse;
import com.mbc.puf.board.dto.InquiryListItem;
import com.mbc.puf.board.dto.InquiryReplyRequest;
import com.mbc.puf.board.dto.InquiryUpdateRequest;
import com.mbc.puf.board.entity.Inquiry;
import com.mbc.puf.board.repository.InquiryRepository;
import com.mbc.puf.common.enums.InquiryStatus;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.query.PagedQuery;
import com.mbc.puf.common.query.QueryConditions;
import com.mbc.puf.common.request.PageParams;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.notification.service.NotificationService;

import jakarta.persistence.EntityManager;

@Service
public class InquiryServiceImpl implements InquiryService {

    // 원글(o) + 작성 고객 이름
    private static final String FROM = " from Inquiry o join Customer c on c.id = o.customerId";

    private final InquiryRepository inquiryRepository;
    private final NotificationService notificationService;   // 고객 알림 센터(문의 분류)
    private final EntityManager em;
    private final com.mbc.puf.stream.StreamPublisher streamPublisher;   // SSE 실시간 이벤트

    public InquiryServiceImpl(InquiryRepository inquiryRepository, NotificationService notificationService,
                              EntityManager em, com.mbc.puf.stream.StreamPublisher streamPublisher) {
        this.inquiryRepository = inquiryRepository;
        this.notificationService = notificationService;
        this.em = em;
        this.streamPublisher = streamPublisher;
    }

    // ---- 고객용 ----

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InquiryListItem> getMyInquiries(Long customerId, int page, int size) {
        QueryConditions c = new QueryConditions()
                .add("o.customerId = ?", customerId)
                .add("o.parentId is null")
                .add("o.deleted = false");
        return fetchList(c, PageParams.of(page, size));
    }

    @Override
    @Transactional(readOnly = true)
    public InquiryDetailResponse getMyInquiry(Long customerId, Long id) {
        Inquiry inquiry = inquiryRepository.findByIdAndParentIdIsNullAndDeletedFalse(id)
                .filter(o -> customerId.equals(o.getCustomerId()))   // 본인 문의만
                .orElseThrow(() -> notFound(id));
        return toDetail(inquiry);
    }

    @Override
    @Transactional
    public InquiryDetailResponse createInquiry(Long customerId, InquiryCreateRequest request) {
        Inquiry inquiry = new Inquiry();
        inquiry.setCustomerId(customerId);
        inquiry.setTitle(request.getTitle().trim());
        inquiry.setContent(request.getContent());
        inquiryRepository.saveAndFlush(inquiry);
        // 모든 관리자에게: 화면 갱신(SSE) + 업무 알림. 문의는 지역 구분이 없다
        streamPublisher.publishToAdmins(null, "inquiry-new", new com.mbc.puf.stream.StreamPayloads.InquiryNew(
                inquiry.getId(), inquiry.getTitle(), java.time.LocalDateTime.now()));
        notificationService.notifyManagers(com.mbc.puf.common.enums.ManagerNotificationCategory.TASK, null,
                "새 1:1 문의", inquiry.getTitle(), "INQUIRY", inquiry.getId());
        return toDetail(inquiry);
    }

    @Override
    @Transactional
    public InquiryDetailResponse updateMyInquiry(Long customerId, Long id, InquiryUpdateRequest request) {
        Inquiry inquiry = requireMyEditable(customerId, id);
        inquiry.setTitle(request.getTitle().trim());
        inquiry.setContent(request.getContent());
        inquiryRepository.flush();
        return toDetail(inquiry);
    }

    @Override
    @Transactional
    public void deleteMyInquiry(Long customerId, Long id) {
        Inquiry inquiry = requireMine(customerId, id);
        inquiry.setDeleted(true);
        // 답변도 같이 지운다 (관리자 화면에서도 함께 사라짐)
        inquiryRepository.findByParentIdAndDeletedFalse(id).ifPresent(reply -> reply.setDeleted(true));
    }

    // ---- 관리자용 ----

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InquiryListItem> getInquiryListForAdmin(String keyword, InquiryStatus status,
                                                                 PeriodQuery period, int page, int size) {
        // 항상 원글(parent_id IS NULL)만, 상태/키워드/작성일 기간은 선택
        QueryConditions c = new QueryConditions()
                .add("o.parentId is null")
                .add("o.deleted = false")
                .eqIfPresent("o.status", status)
                .containsIfPresent(keyword, "o.title", "o.content")
                .periodIfPresent("o.createdAt", period);
        return fetchList(c, PageParams.of(page, size));
    }

    @Override
    @Transactional(readOnly = true)
    public InquiryDetailResponse getInquiryForAdmin(Long id) {
        return toDetail(requireOriginal(id));
    }

    @Override
    @Transactional
    public void reply(Long id, Long authorManagerId, InquiryReplyRequest request) {
        Inquiry original = requireOriginal(id);
        if (original.getStatus() != InquiryStatus.PENDING) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 답변이 등록된 문의입니다.");
        }

        Inquiry reply = new Inquiry();
        reply.setParentId(id);
        reply.setAuthorManagerId(authorManagerId);
        reply.setContent(request.getContent());
        try {
            inquiryRepository.saveAndFlush(reply);
        } catch (DataIntegrityViolationException e) {
            // 동시에 답변이 등록된 경우에 대한 방어 (살아있는 답글은 원글당 1개: 부분 유니크 인덱스)
            throw new BusinessException(HttpStatus.CONFLICT, "이미 답변이 등록된 문의입니다.");
        }
        original.setStatus(InquiryStatus.ANSWERED);

        // 고객 알림 센터(문의 분류): 내 문의에 답변이 달렸다고 알린다
        notificationService.notifyCustomerInquiryAnswered(original.getCustomerId(), original.getId(),
                original.getTitle());
        publishInquiryChanged(original, "UPDATE");
    }

    @Override
    @Transactional
    public void updateReply(Long id, InquiryReplyRequest request) {
        Inquiry original = requireAnswered(id);
        inquiryRepository.findByParentIdAndDeletedFalse(id).ifPresent(reply -> reply.setContent(request.getContent()));
        publishInquiryChanged(original, "UPDATE");
    }

    @Override
    @Transactional
    public void deleteReply(Long id) {
        Inquiry original = requireAnswered(id);
        inquiryRepository.findByParentIdAndDeletedFalse(id).ifPresent(reply -> reply.setDeleted(true));
        original.setStatus(InquiryStatus.PENDING);   // 다시 답변대기 목록에 노출되어 재답변 가능
        publishInquiryChanged(original, "UPDATE");
    }

    // 문의 답변이 등록/수정/삭제되면 작성한 고객과 관리자에게 다시 조회하라고 알린다 (문의 id 기준)
    private void publishInquiryChanged(Inquiry original, String action) {
        streamPublisher.dataChangedToCustomer(original.getCustomerId(), "inquiry", action, original.getId());
        streamPublisher.dataChangedToAdmins(null, "inquiry", action, original.getId());
    }

    // ---- 내부 메서드 ----

    private PageResponse<InquiryListItem> fetchList(QueryConditions c, PageParams pageParams) {
        return PagedQuery.fetch(em, "select o, c.name" + FROM, "select count(o)" + FROM, c,
                " order by o.createdAt desc, o.id desc", Object[].class, row -> {
                    Inquiry o = (Inquiry) row[0];
                    InquiryListItem item = new InquiryListItem();
                    item.setId(o.getId());
                    item.setCustomerName((String) row[1]);
                    item.setTitle(o.getTitle());
                    item.setStatus(o.getStatus());
                    item.setCreatedAt(o.getCreatedAt());
                    return item;
                }, pageParams);
    }

    // 삭제되지 않은 원글, 없으면 404
    private Inquiry requireOriginal(Long id) {
        return inquiryRepository.findByIdAndParentIdIsNullAndDeletedFalse(id).orElseThrow(() -> notFound(id));
    }

    // 내 원글만. 남의 글/없는 글은 404
    private Inquiry requireMine(Long customerId, Long id) {
        return inquiryRepository.findByIdAndParentIdIsNullAndDeletedFalse(id)
                .filter(o -> customerId.equals(o.getCustomerId()))
                .orElseThrow(() -> notFound(id));
    }

    // 수정은 아직 답변 전(PENDING)인 내 원글만. 답변 후는 409
    private Inquiry requireMyEditable(Long customerId, Long id) {
        Inquiry inquiry = requireMine(customerId, id);
        if (inquiry.getStatus() != InquiryStatus.PENDING) {
            throw new BusinessException(HttpStatus.CONFLICT, "답변이 등록된 문의는 수정할 수 없습니다.");
        }
        return inquiry;
    }

    // 답변이 달린 원글만, 아니면 409
    private Inquiry requireAnswered(Long id) {
        Inquiry original = requireOriginal(id);
        if (original.getStatus() != InquiryStatus.ANSWERED) {
            throw new BusinessException(HttpStatus.CONFLICT, "아직 답변이 등록되지 않은 문의입니다.");
        }
        return original;
    }

    // 원글 + (있으면) 살아있는 답글 + 답변 관리자 이름
    private InquiryDetailResponse toDetail(Inquiry o) {
        InquiryDetailResponse detail = new InquiryDetailResponse();
        detail.setId(o.getId());
        detail.setCustomerName(em.createQuery("select c.name from Customer c where c.id = :id", String.class)
                .setParameter("id", o.getCustomerId()).getSingleResult());
        detail.setTitle(o.getTitle());
        detail.setContent(o.getContent());
        detail.setStatus(o.getStatus());
        detail.setCreatedAt(o.getCreatedAt());
        inquiryRepository.findByParentIdAndDeletedFalse(o.getId()).ifPresent(reply -> {
            detail.setReplyContent(reply.getContent());
            detail.setReplyAuthorName(BoardAuthors.managerName(em, reply.getAuthorManagerId()));
            detail.setRepliedAt(reply.getCreatedAt());
        });
        return detail;
    }

    private BusinessException notFound(Long id) {
        return new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 문의입니다. id=" + id);
    }
}