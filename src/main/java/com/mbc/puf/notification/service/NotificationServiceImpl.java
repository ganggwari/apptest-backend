package com.mbc.puf.notification.service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.AlertType;
import com.mbc.puf.common.enums.CustomerNotificationCategory;
import com.mbc.puf.common.enums.ManagerNotificationCategory;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.query.PagedQuery;
import com.mbc.puf.common.query.QueryConditions;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.member.repository.ManagerRegionRepository;
import com.mbc.puf.notification.dto.CustomerNotificationItem;
import com.mbc.puf.notification.dto.ManagerNotificationItem;
import com.mbc.puf.notification.dto.NotificationUnreadResponse;
import com.mbc.puf.notification.entity.CustomerNotification;
import com.mbc.puf.notification.entity.ManagerNotification;
import com.mbc.puf.notification.repository.CustomerNotificationRepository;
import com.mbc.puf.notification.repository.ManagerNotificationRepository;
import com.mbc.puf.stream.StreamPayloads;
import com.mbc.puf.stream.StreamPublisher;

import jakarta.persistence.EntityManager;

@Service
public class NotificationServiceImpl implements NotificationService {

    // 담당 지역이 하나도 없을 때 IN 절이 비지 않도록 넣는 값 (존재할 수 없는 id → 결과 0건)
    private static final List<Long> NONE = List.of(-1L);

    // 내가 읽은 알림인지 (관리자별 읽음). %s 자리에는 내 관리자 id 자리표시자가 들어간다
    private static final String READ_EXISTS =
            "exists (select 1 from ManagerNotificationRead r where r.notificationId = n.id and r.managerId = %s)";

    private final CustomerNotificationRepository customerRepository;
    private final ManagerNotificationRepository managerRepository;
    private final ManagerRegionRepository managerRegionRepository;
    private final EntityManager em;
    private final StreamPublisher streamPublisher;
    private final PushService pushService;   // FCM 푸시 (설정이 없으면 아무것도 안 함)

    public NotificationServiceImpl(CustomerNotificationRepository customerRepository,
                                   ManagerNotificationRepository managerRepository,
                                   ManagerRegionRepository managerRegionRepository, EntityManager em,
                                   StreamPublisher streamPublisher, PushService pushService) {
        this.customerRepository = customerRepository;
        this.managerRepository = managerRepository;
        this.managerRegionRepository = managerRegionRepository;
        this.em = em;
        this.streamPublisher = streamPublisher;
        this.pushService = pushService;
    }

    // ================= 고객 알림: 만들기 =================

    @Override
    @Transactional
    public void notifyCustomerRisk(Long customerId, Long alertId, String underpassName, String carNum,
                                   AlertType alertType, String reason) {
        CustomerNotification n = new CustomerNotification();
        n.setCustomerId(customerId);
        n.setCategory(CustomerNotificationCategory.RISK);
        n.setTitle(alertType == AlertType.FINAL ? "지하차도 진입 직전 알림" : "지하차도 접근 알림");
        n.setBody((underpassName == null ? "" : underpassName + " · ") + carNum
                + (reason == null || reason.isBlank() ? "" : " · 사유: " + reason));
        n.setRefType("ALERT");
        n.setRefId(alertId);
        customerRepository.save(n);
        pushToCustomer(n);
    }

    @Override
    @Transactional
    public void notifyCustomersNewNotice(Long noticeId, String noticeTitle, boolean important) {
        customerRepository.insertNoticeForAllActiveCustomers(noticeId, important ? "[긴급] 새 공지" : "새 공지",
                noticeTitle);
        // 고객 수만큼 알림 행이 한 번에 생기므로 개인 이벤트 대신 공개 이벤트 1건만 보낸다.
        // 로그인한 고객의 프런트는 이걸 받으면 안 읽은 알림 개수를 다시 조회한다
        streamPublisher.publishPublic("notice-new", null, new StreamPayloads.NoticeNew(noticeId, noticeTitle, important));
        // 앱이 닫혀 있어도 알려 주는 푸시 (FCM 설정이 있을 때만)
        pushService.sendToAllCustomers(important ? "[긴급] 새 공지" : "새 공지", noticeTitle, "NOTICE", noticeId);
    }

    @Override
    @Transactional
    public void notifyCustomerInquiryAnswered(Long customerId, Long inquiryId, String inquiryTitle) {
        CustomerNotification n = new CustomerNotification();
        n.setCustomerId(customerId);
        n.setCategory(CustomerNotificationCategory.INQUIRY);
        n.setTitle("내 문의에 답변이 등록되었습니다");
        n.setBody(inquiryTitle);
        n.setRefType("INQUIRY");
        n.setRefId(inquiryId);
        customerRepository.save(n);
        pushToCustomer(n);
    }

    // ================= 고객 알림: 조회·읽음 =================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerNotificationItem> getMyNotifications(Long customerId,
                                                                    CustomerNotificationCategory category,
                                                                    boolean unreadOnly, PageQuery page) {
        QueryConditions c = new QueryConditions()
                .add("n.customerId = ?", customerId)   // 본인 것만
                .eqIfPresent("n.category", category)
                .addIf(unreadOnly, "n.read = false");
        return PagedQuery.fetch(em, "select n from CustomerNotification n", "select count(n) from CustomerNotification n",
                c, " order by n.createdAt desc, n.id desc", CustomerNotification.class,
                n -> new CustomerNotificationItem(n.getId(), n.getCategory(), n.getTitle(), n.getBody(),
                        n.getRefType(), n.getRefId(), n.isRead(), n.getCreatedAt()),
                page.toParams());
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationUnreadResponse getMyUnread(Long customerId) {
        List<Object[]> rows = em.createQuery("select n.category, count(n) from CustomerNotification n"
                        + " where n.customerId = :id and n.read = false group by n.category", Object[].class)
                .setParameter("id", customerId).getResultList();
        Map<CustomerNotificationCategory, Long> counts = new EnumMap<>(CustomerNotificationCategory.class);
        for (Object[] row : rows) {
            counts.put((CustomerNotificationCategory) row[0], (Long) row[1]);
        }
        return toUnread(CustomerNotificationCategory.values(), counts);
    }

    @Override
    @Transactional
    public void markMyRead(Long customerId, Long id) {
        CustomerNotification n = customerRepository.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> notFound(id));
        n.setRead(true);
    }

    @Override
    @Transactional
    public void markMyAllRead(Long customerId, CustomerNotificationCategory category) {
        if (category == null) {
            customerRepository.markAllRead(customerId);
        } else {
            customerRepository.markAllReadByCategory(customerId, category);
        }
    }

    // ================= 관리자 알림 =================

    @Override
    @Transactional
    public void notifyManagers(ManagerNotificationCategory category, Long regionId, String title, String body,
                               String refType, Long refId) {
        ManagerNotification n = new ManagerNotification();
        n.setCategory(category);
        n.setRegionId(regionId);
        n.setTitle(title);
        n.setBody(body);
        n.setRefType(refType);
        n.setRefId(refId);
        managerRepository.save(n);
        // 해당 지역 담당 관리자(regionId 가 null 이면 모든 관리자)의 화면에 새 알림을 알린다
        streamPublisher.publishToAdmins(regionId, "notification-new",
                new StreamPayloads.ManagerNotificationNew(n.getId(), n.getCategory(), n.getRegionId(), n.getTitle(),
                        n.getBody(), n.getRefType(), n.getRefId(), createdOrNow(n.getCreatedAt())));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ManagerNotificationItem> getManagerNotifications(LoginUser loginUser,
                                                                        ManagerNotificationCategory category,
                                                                        boolean unreadOnly, PageQuery page) {
        Long me = loginUser.id();
        QueryConditions c = new QueryConditions();
        c.add("(n.regionId is null or n.regionId in (" + c.param(myRegionIds(me)) + "))")
                .eqIfPresent("n.category", category);
        if (unreadOnly) {
            c.add("not " + READ_EXISTS.formatted("?"), me);
        }
        PageResponse<ManagerNotification> found = PagedQuery.fetch(em, "select n from ManagerNotification n",
                "select count(n) from ManagerNotification n", c, " order by n.createdAt desc, n.id desc",
                ManagerNotification.class, n -> n, page.toParams());

        // 이번 페이지 알림 중 내가 읽은 것만 따로 조회해서 read 를 채운다
        List<Long> ids = found.getItems().stream().map(ManagerNotification::getId).toList();
        Set<Long> readIds = ids.isEmpty() ? Set.of() : new HashSet<>(em.createQuery(
                        "select r.notificationId from ManagerNotificationRead r"
                                + " where r.managerId = :me and r.notificationId in (:ids)", Long.class)
                .setParameter("me", me).setParameter("ids", ids).getResultList());

        List<ManagerNotificationItem> items = found.getItems().stream()
                .map(n -> new ManagerNotificationItem(n.getId(), n.getCategory(), n.getRegionId(), n.getTitle(),
                        n.getBody(), n.getRefType(), n.getRefId(), readIds.contains(n.getId()), n.getCreatedAt()))
                .toList();
        return new PageResponse<>(items, found.getPage(), found.getSize(), found.getTotalItems());
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationUnreadResponse getManagerUnread(LoginUser loginUser) {
        List<Object[]> rows = em.createQuery("select n.category, count(n) from ManagerNotification n"
                        + " where (n.regionId is null or n.regionId in (:regions))"
                        + " and not " + READ_EXISTS.formatted(":me") + " group by n.category", Object[].class)
                .setParameter("regions", myRegionIds(loginUser.id()))
                .setParameter("me", loginUser.id()).getResultList();
        Map<ManagerNotificationCategory, Long> counts = new EnumMap<>(ManagerNotificationCategory.class);
        for (Object[] row : rows) {
            counts.put((ManagerNotificationCategory) row[0], (Long) row[1]);
        }
        return toUnread(ManagerNotificationCategory.values(), counts);
    }

    @Override
    @Transactional
    public void markManagerRead(LoginUser loginUser, Long id) {
        // 내 담당 지역(또는 전체 대상) 알림만. 남의 지역 알림은 존재하지 않는 것처럼 404
        Long visible = em.createQuery("select count(n) from ManagerNotification n where n.id = :id"
                        + " and (n.regionId is null or n.regionId in (:regions))", Long.class)
                .setParameter("id", id).setParameter("regions", myRegionIds(loginUser.id())).getSingleResult();
        if (visible == 0) {
            throw notFound(id);
        }
        em.createNativeQuery("INSERT INTO manager_notification_read (notification_id, manager_id)"
                        + " VALUES (:id, :me) ON CONFLICT DO NOTHING")
                .setParameter("id", id).setParameter("me", loginUser.id()).executeUpdate();
    }

    @Override
    @Transactional
    public void markManagerAllRead(LoginUser loginUser, ManagerNotificationCategory category) {
        String sql = "INSERT INTO manager_notification_read (notification_id, manager_id)"
                + " SELECT n.id, :me FROM manager_notification n"
                + " WHERE (n.region_id IS NULL OR n.region_id IN (:regions))"
                + (category != null ? " AND CAST(n.category AS TEXT) = :cat" : "")
                + " ON CONFLICT DO NOTHING";
        var q = em.createNativeQuery(sql)
                .setParameter("me", loginUser.id())
                .setParameter("regions", myRegionIds(loginUser.id()));
        if (category != null) {
            q.setParameter("cat", category.name());
        }
        q.executeUpdate();
    }

    // ================= 내부 =================

    // 방금 저장한 고객 알림을 그 고객의 SSE 로 보낸다 (안 읽은 개수 포함)
    private void pushToCustomer(CustomerNotification n) {
        // 앱·웹이 닫혀 있어도 받도록 FCM 푸시도 같이 보낸다 (설정이 없으면 건너뜀)
        pushService.sendToCustomer(n.getCustomerId(), n.getTitle(), n.getBody(), n.getRefType(), n.getRefId());
        streamPublisher.publishToCustomer(n.getCustomerId(), "notification-new",
                new StreamPayloads.CustomerNotificationNew(n.getId(), n.getCategory(), n.getTitle(), n.getBody(),
                        n.getRefType(), n.getRefId(), createdOrNow(n.getCreatedAt()), getMyUnread(n.getCustomerId())));
    }

    private LocalDateTime createdOrNow(LocalDateTime createdAt) {
        return createdAt != null ? createdAt : LocalDateTime.now();
    }

    private List<Long> myRegionIds(Long managerId) {
        List<Long> mine = managerRegionRepository.findRegionIds(managerId);
        return mine.isEmpty() ? NONE : mine;
    }

    private <E extends Enum<E>> NotificationUnreadResponse toUnread(E[] all, Map<E, Long> counts) {
        Map<String, Long> byCategory = new LinkedHashMap<>();
        long total = 0;
        for (E category : all) {
            long count = counts.getOrDefault(category, 0L);
            byCategory.put(category.name(), count);
            total += count;
        }
        return new NotificationUnreadResponse(total, byCategory);
    }

    private BusinessException notFound(Long id) {
        return new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 알림입니다. id=" + id);
    }
}