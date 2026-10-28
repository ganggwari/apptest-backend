package com.mbc.puf.board.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.alert.entity.NoticeDelivery;
import com.mbc.puf.alert.repository.NoticeAlertRepository;
import com.mbc.puf.alert.repository.NoticeDeliveryRepository;
import com.mbc.puf.board.dto.NoticeCreateRequest;
import com.mbc.puf.board.dto.NoticeDetailResponse;
import com.mbc.puf.board.dto.NoticeListItem;
import com.mbc.puf.board.dto.NoticeUpdateRequest;
import com.mbc.puf.board.entity.Notice;
import com.mbc.puf.board.repository.NoticeRepository;
import com.mbc.puf.common.enums.AlertChannel;
import com.mbc.puf.common.enums.DeliveryStatus;
import com.mbc.puf.common.enums.NoticeCategory;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.notification.service.NotificationService;
import com.mbc.puf.common.query.PagedQuery;
import com.mbc.puf.common.query.QueryConditions;
import com.mbc.puf.common.request.PageParams;
import com.mbc.puf.common.response.PageResponse;

import jakarta.persistence.EntityManager;
import java.util.List;

@Service
public class NoticeServiceImpl implements NoticeService {

    // 작성 관리자 이름은 employee 에서 가져온다
    private static final String FROM = " from Notice n join Manager m on m.id = n.authorId"
            + " join Employee e on e.id = m.employeeId";

    private final NoticeRepository noticeRepository;
    private final NoticeAlertRepository noticeAlertRepository;
    private final NoticeDeliveryRepository noticeDeliveryRepository;
    private final DbClock dbClock;
    private final NotificationService notificationService;   // 고객 알림 센터(공지 분류)
    private final EntityManager em;
    private final com.mbc.puf.stream.StreamPublisher streamPublisher;   // SSE 실시간 이벤트

    public NoticeServiceImpl(NoticeRepository noticeRepository,
                             NoticeAlertRepository noticeAlertRepository,
                             NoticeDeliveryRepository noticeDeliveryRepository,
                             DbClock dbClock, NotificationService notificationService, EntityManager em,
                             com.mbc.puf.stream.StreamPublisher streamPublisher) {
        this.streamPublisher = streamPublisher;
        this.noticeRepository = noticeRepository;
        this.noticeAlertRepository = noticeAlertRepository;
        this.noticeDeliveryRepository = noticeDeliveryRepository;
        this.dbClock = dbClock;
        this.notificationService = notificationService;
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NoticeListItem> getNoticeList(String keyword, String authorName, NoticeCategory category, Boolean important, int page, int size) {
        PageParams pageParams = PageParams.of(page, size);
        QueryConditions c = new QueryConditions()
                .add("n.deleted = false")
                .containsIfPresent(keyword, "n.title", "n.content")   // 제목+내용 통합 검색
                .containsIfPresent(authorName, "e.name")              // 작성 관리자 이름
                .eqIfPresent("n.category", category)                  // null 이면 전체 분류
                .eqIfPresent("n.important", important);               // null 이면 전체

        return PagedQuery.fetch(em, "select n, e.name" + FROM, "select count(n)" + FROM, c,
                " order by n.important desc, n.createdAt desc, n.id desc", Object[].class, row -> {
                    Notice n = (Notice) row[0];
                    NoticeListItem item = new NoticeListItem();
                    item.setId(n.getId());
                    item.setTitle(n.getTitle());
                    item.setCategory(n.getCategory());
                    item.setImportant(n.isImportant());
                    item.setViewCount(n.getViewCount());
                    item.setAuthorName((String) row[1]);
                    item.setCreatedAt(n.getCreatedAt());
                    return item;
                }, pageParams);
    }

    @Override
    @Transactional
    public NoticeDetailResponse getNotice(Long id, boolean countView) {
        Notice notice = getActiveNotice(id);   // 없으면 404
        if (countView) {
            noticeRepository.increaseViewCount(id);
            notice = getActiveNotice(id);      // 올라간 조회수를 다시 읽는다
        }
        return toDetail(notice);
    }

    @Override
    @Transactional
    public NoticeDetailResponse createNotice(Long authorId, NoticeCreateRequest request) {
        Notice notice = new Notice();
        notice.setTitle(request.getTitle().trim());
        notice.setContent(request.getContent());
        notice.setCategory(request.getCategory() == null ? NoticeCategory.INFO : request.getCategory());
        notice.setImportant(request.isImportant());
        // 고객 알림 발송 여부: 관리자가 체크한 값. 안 보내면 긴급 분류만 보낸다 (관성적 공지에 알림이 가지 않도록)
        boolean sendPush = request.getSendPush() != null
                ? request.getSendPush() : notice.getCategory() == NoticeCategory.URGENT;
        notice.setSendPush(sendPush);
        notice.setAuthorId(authorId);
        noticeRepository.saveAndFlush(notice);   // id 확보
        streamPublisher.dataChangedPublic(null, "notice", "CREATE", notice.getId());

        if (!sendPush) {
            return toDetail(notice);   // 알림을 보내지 않는 공지는 여기서 끝 (목록에만 올라간다)
        }

        // 고객 알림 센터(공지 분류): 활성 고객 전체에게 한 건씩
        // 긴급 분류이거나 중요 공지면 "[긴급]" 으로 표시
        notificationService.notifyCustomersNewNotice(notice.getId(), notice.getTitle(),
                notice.isImportant() || notice.getCategory() == NoticeCategory.URGENT);

        // 활성 고객 전체에게 notice_alert 일괄 삽입 (native bulk insert)
        int inserted = noticeAlertRepository.insertForAllActiveCustomers(notice.getId());

        // 삽입된 notice_alert 행들에 notice_delivery(SMS PENDING) 기록
        if (inserted > 0) {
            em.flush();   // insertForAllActiveCustomers @Modifying 반영 후 읽기
            List<Long> alertIds = em.createNativeQuery(
                            "SELECT id FROM notice_alert WHERE notice_id = :noticeId")
                    .setParameter("noticeId", notice.getId())
                    .getResultList()
                    .stream()
                    .map(o -> ((Number) o).longValue())
                    .toList();

            for (Long alertId : alertIds) {
                NoticeDelivery d = new NoticeDelivery();
                d.setNoticeAlertId(alertId);
                d.setChannel(AlertChannel.SMS);
                d.setTokenId(null);   // SMS는 token 없음
                d.setStatus(DeliveryStatus.PENDING);
                noticeDeliveryRepository.save(d);
            }
        }

        return toDetail(notice);
    }

    @Override
    @Transactional
    public NoticeDetailResponse updateNotice(Long id, NoticeUpdateRequest request) {
        Notice notice = getActiveNotice(id);   // 존재/삭제 여부 확인, 없으면 404
        notice.setTitle(request.getTitle().trim());
        notice.setContent(request.getContent());
        notice.setCategory(request.getCategory() == null ? notice.getCategory() : request.getCategory());   // 안 보내면 기존 분류 유지
        notice.setImportant(request.isImportant());
        notice.setUpdatedAt(dbClock.now());
        streamPublisher.dataChangedPublic(null, "notice", "UPDATE", notice.getId());
        return toDetail(notice);
    }

    @Override
    @Transactional
    public void deleteNotice(Long id) {
        getActiveNotice(id).setDeleted(true);
        streamPublisher.dataChangedPublic(null, "notice", "DELETE", id);
    }

    // ---- 내부 메서드 ----

    // 삭제되지 않은 공지사항, 없으면 404
    private Notice getActiveNotice(Long id) {
        return noticeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 공지사항입니다. id=" + id));
    }

    private NoticeDetailResponse toDetail(Notice n) {
        NoticeDetailResponse detail = new NoticeDetailResponse();
        detail.setId(n.getId());
        detail.setTitle(n.getTitle());
        detail.setContent(n.getContent());
        detail.setCategory(n.getCategory());
        detail.setImportant(n.isImportant());
        detail.setSendPush(n.isSendPush());
        detail.setViewCount(n.getViewCount());
        detail.setAuthorId(n.getAuthorId());
        detail.setAuthorName(BoardAuthors.managerName(em, n.getAuthorId()));
        detail.setCreatedAt(n.getCreatedAt());
        detail.setUpdatedAt(n.getUpdatedAt());
        return detail;
    }
}