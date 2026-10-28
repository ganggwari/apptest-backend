package com.mbc.puf.alert.service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.alert.dto.AlertLogListItem;
import com.mbc.puf.alert.dto.AlertLogListItem.ChannelResult;
import com.mbc.puf.alert.dto.AlertLogQuery;
import com.mbc.puf.common.enums.AlertChannel;
import com.mbc.puf.common.enums.AlertType;
import com.mbc.puf.common.enums.DeliveryStatus;
import com.mbc.puf.common.query.PagedQuery;
import com.mbc.puf.common.query.QueryConditions;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.request.VehicleQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;

import jakarta.persistence.EntityManager;

@Service
public class AlertLogServiceImpl implements AlertLogService {

    private static final String ENUM_PKG = "com.mbc.puf.common.enums.";

    // 위험 알림: 알림 -> 이벤트 -> 지하차도 -> 지역, + 수신 고객
    private static final String ALERT_JOIN = " join UnderpassEvent ue on ue.id = a.eventId"
            + " join Underpass u on u.id = ue.underpassId join Region r on r.id = u.regionId"
            + " join Customer c on c.id = a.customerId";
    private static final String ALERT_FROM = " from Alert a" + ALERT_JOIN;
    private static final String ALERT_DELIVERY_FROM = " from AlertDelivery d join Alert a on a.id = d.alertId"
            + ALERT_JOIN + " left join DeviceToken t on t.id = d.tokenId";

    // 공지 알림: 공지 알림 -> 공지, + 수신 고객
    private static final String NOTICE_JOIN = " join Notice n on n.id = na.noticeId"
            + " join Customer c on c.id = na.customerId";
    private static final String NOTICE_FROM = " from NoticeAlert na" + NOTICE_JOIN;
    private static final String NOTICE_DELIVERY_FROM = " from NoticeDelivery d join NoticeAlert na on na.id = d.noticeAlertId"
            + NOTICE_JOIN + " left join DeviceToken t on t.id = d.tokenId";

    private static final String PUSH_CHANNELS = "(" + ENUM_PKG + "AlertChannel.PUSH_WEB, "
            + ENUM_PKG + "AlertChannel.PUSH_APP)";

    private final ScopeResolver scopeResolver;
    private final EntityManager em;

    public AlertLogServiceImpl(ScopeResolver scopeResolver, EntityManager em) {
        this.scopeResolver = scopeResolver;
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AlertLogListItem> getAlerts(LoginUser loginUser, ScopeQuery scope, AlertLogQuery q,
                                                    VehicleQuery vehicle, PeriodQuery period, PageQuery page) {
        QueryConditions c = alertConditions(loginUser, scope, q, vehicle).periodIfPresent("a.sentAt", period);
        existsDelivery(c, "AlertDelivery", "alertId", "a.id", q);
        PageResponse<AlertLogListItem> result = PagedQuery.fetch(em,
                "select a, ue.underpassId, u.name, u.regionId, r.name, c.name" + ALERT_FROM,
                "select count(a)" + ALERT_FROM, c, " order by a.sentAt desc, a.id desc", Object[].class,
                row -> {
                    var a = (com.mbc.puf.alert.entity.Alert) row[0];
                    return alertItem(a, null, null, null, (Long) row[1], (String) row[2], (Long) row[3],
                            (String) row[4], (String) row[5], a.getSentAt());
                }, page.toParams());
        return withChannels(result, "select d.alertId, d.channel, d.status from AlertDelivery d where d.alertId in :ids");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AlertLogListItem> getAlertDeliveries(LoginUser loginUser, ScopeQuery scope, AlertLogQuery q,
                                                             VehicleQuery vehicle, PeriodQuery period, PageQuery page) {
        QueryConditions c = alertConditions(loginUser, scope, q, vehicle)
                .eqIfPresent("d.channel", q.getChannel())
                .eqIfPresent("d.status", q.getStatus())
                .periodIfPresent("d.sentAt", period);
        return PagedQuery.fetch(em,
                "select d, a, ue.underpassId, u.name, u.regionId, r.name, c.name, t.platform" + ALERT_DELIVERY_FROM,
                "select count(d)" + ALERT_DELIVERY_FROM, c, " order by d.sentAt desc, d.id desc", Object[].class,
                row -> {
                    var d = (com.mbc.puf.alert.entity.AlertDelivery) row[0];
                    var a = (com.mbc.puf.alert.entity.Alert) row[1];
                    return alertItem(a, d.getId(), d.getChannel(), d.getStatus(), (Long) row[2], (String) row[3],
                            (Long) row[4], (String) row[5], (String) row[6], d.getSentAt(), platformOf(row[7]));
                }, page.toParams());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AlertLogListItem> getNotices(AlertLogQuery q, PeriodQuery period, PageQuery page) {
        QueryConditions c = noticeConditions(q).periodIfPresent("na.createdAt", period);
        existsDelivery(c, "NoticeDelivery", "noticeAlertId", "na.id", q);
        PageResponse<AlertLogListItem> result = PagedQuery.fetch(em,
                "select na, n.title, c.name" + NOTICE_FROM, "select count(na)" + NOTICE_FROM, c,
                " order by na.createdAt desc, na.id desc", Object[].class, row -> {
                    var na = (com.mbc.puf.alert.entity.NoticeAlert) row[0];
                    return noticeItem(na, null, null, null, (String) row[1], (String) row[2], na.getCreatedAt(), null);
                }, page.toParams());
        return withChannels(result,
                "select d.noticeAlertId, d.channel, d.status from NoticeDelivery d where d.noticeAlertId in :ids");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AlertLogListItem> getNoticeDeliveries(AlertLogQuery q, PeriodQuery period, PageQuery page) {
        QueryConditions c = noticeConditions(q)
                .eqIfPresent("d.channel", q.getChannel())
                .eqIfPresent("d.status", q.getStatus())
                .periodIfPresent("d.sentAt", period);
        return PagedQuery.fetch(em,
                "select d, na, n.title, c.name, t.platform" + NOTICE_DELIVERY_FROM,
                "select count(d)" + NOTICE_DELIVERY_FROM, c, " order by d.sentAt desc, d.id desc", Object[].class,
                row -> {
                    var d = (com.mbc.puf.alert.entity.NoticeDelivery) row[0];
                    var na = (com.mbc.puf.alert.entity.NoticeAlert) row[1];
                    return noticeItem(na, d, d.getChannel(), d.getStatus(), (String) row[2], (String) row[3],
                            d.getSentAt(), platformOf(row[4]));
                }, page.toParams());
    }

    // ---- 조건 ----

    private QueryConditions alertConditions(LoginUser loginUser, ScopeQuery scope, AlertLogQuery q, VehicleQuery vehicle) {
        // 관리자는 지역을 안 주면 담당 지역 전체, 주면 그 지역만(담당 아니면 403)
        ScopeQuery resolved = scopeResolver.resolve(loginUser, scope);
        return new QueryConditions()
                .eqIfPresent("ue.underpassId", resolved.getUnderpassId())
                .inIfPresent("u.regionId", resolved.getRegionIds())
                .eqIfPresent("a.customerId", q.getCustomerId())
                .containsIfPresent(q.getCustomerName(), "c.name")
                .eqIfPresent("a.alertType", q.getAlertType())
                .carNumIfPresent(vehicle.getCarNum(), "a.carNum");
    }

    private QueryConditions noticeConditions(AlertLogQuery q) {
        return new QueryConditions()
                .eqIfPresent("na.noticeId", q.getNoticeId())
                .eqIfPresent("na.customerId", q.getCustomerId())
                .containsIfPresent(q.getCustomerName(), "c.name");
    }

    // 알림 단위 목록: channel/status 필터 + 받음/미수신 필터. 열거값은 이름 고정 문자열이라 직접 붙여도 안전
    private void existsDelivery(QueryConditions c, String entity, String fk, String owner, AlertLogQuery q) {
        if (q.getChannel() != null || q.getStatus() != null) {
            String cond = "exists (select 1 from " + entity + " d where d." + fk + " = " + owner
                    + (q.getChannel() == null ? "" : " and d.channel = " + ENUM_PKG + "AlertChannel." + q.getChannel().name())
                    + (q.getStatus() == null ? "" : " and d.status = " + ENUM_PKG + "DeliveryStatus." + q.getStatus().name())
                    + ")";
            c.add(cond);
        }
        if (q.getReceived() != null) {
            // 받음 = 푸시 채널에 SUCCESS 가 하나라도 있음 (문자는 기준에서 뺀다)
            c.add((q.getReceived() ? "exists" : "not exists") + " (select 1 from " + entity + " d where d." + fk
                    + " = " + owner + " and d.channel in " + PUSH_CHANNELS
                    + " and d.status = " + ENUM_PKG + "DeliveryStatus.SUCCESS)");
        }
    }

    // ---- 변환 ----

    private static String platformOf(Object platform) {
        return platform == null ? null : platform.toString();
    }

    private static AlertLogListItem alertItem(com.mbc.puf.alert.entity.Alert a, Long deliveryId, AlertChannel channel,
                                              DeliveryStatus status, Long underpassId, String underpassName,
                                              Long regionId, String regionName, String customerName,
                                              LocalDateTime at) {
        return alertItem(a, deliveryId, channel, status, underpassId, underpassName, regionId, regionName,
                customerName, at, null);
    }

    private static AlertLogListItem alertItem(com.mbc.puf.alert.entity.Alert a, Long deliveryId, AlertChannel channel,
                                              DeliveryStatus status, Long underpassId, String underpassName,
                                              Long regionId, String regionName, String customerName,
                                              LocalDateTime at, String platform) {
        String title = a.getAlertType() == AlertType.FINAL ? "최종 알림" : "접근 알림";
        return new AlertLogListItem("ALERT", a.getId(), deliveryId, a.getEventId(), title, regionId, regionName,
                underpassId, underpassName, a.getCustomerId(), customerName, a.getCarId(), a.getCarNum(),
                a.getAlertType(), channel, platform, status, null, null, a.isRead(), at);
    }

    private static AlertLogListItem noticeItem(com.mbc.puf.alert.entity.NoticeAlert na,
                                               com.mbc.puf.alert.entity.NoticeDelivery d, AlertChannel channel,
                                               DeliveryStatus status, String title, String customerName,
                                               LocalDateTime at, String platform) {
        return new AlertLogListItem("NOTICE", na.getId(), d == null ? null : d.getId(), na.getNoticeId(), title,
                null, null, null, null, na.getCustomerId(), customerName, null, null, null, channel, platform,
                status, null, null, na.isRead(), at);
    }

    // 이 페이지 알림들의 채널별 결과를 한 번에 붙인다 (알림마다 조회하지 않게 모아서)
    private PageResponse<AlertLogListItem> withChannels(PageResponse<AlertLogListItem> page, String jpql) {
        if (page.getItems().isEmpty()) {
            return page;
        }
        List<Long> ids = page.getItems().stream().map(AlertLogListItem::id).toList();
        Map<Long, Map<AlertChannel, DeliveryStatus>> byOwner = new HashMap<>();
        for (Object[] row : em.createQuery(jpql, Object[].class).setParameter("ids", ids).getResultList()) {
            byOwner.computeIfAbsent((Long) row[0], k -> new EnumMap<>(AlertChannel.class))
                    .merge((AlertChannel) row[1], (DeliveryStatus) row[2], AlertLogServiceImpl::better);
        }
        List<AlertLogListItem> items = page.getItems().stream().map(item -> {
            Map<AlertChannel, DeliveryStatus> m = byOwner.getOrDefault(item.id(), Map.of());
            List<ChannelResult> channels = m.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> new ChannelResult(e.getKey(), e.getValue())).toList();
            return item.withDelivery(channels, pushStatus(m));
        }).toList();
        return new PageResponse<>(items, page.getPage(), page.getSize(), page.getTotalItems());
    }

    // 한 채널에 여러 번 시도했을 때 대표 결과: SUCCESS > PENDING > FAIL
    private static DeliveryStatus better(DeliveryStatus x, DeliveryStatus y) {
        return rank(x) >= rank(y) ? x : y;
    }

    private static int rank(DeliveryStatus s) {
        return s == DeliveryStatus.SUCCESS ? 2 : s == DeliveryStatus.PENDING ? 1 : 0;
    }

    private static String pushStatus(Map<AlertChannel, DeliveryStatus> m) {
        DeliveryStatus web = m.get(AlertChannel.PUSH_WEB);
        DeliveryStatus app = m.get(AlertChannel.PUSH_APP);
        if (web == null && app == null) {
            return "NO_PUSH";
        }
        DeliveryStatus best = web == null ? app : app == null ? web : better(web, app);
        return best.name();
    }
}
