package com.mbc.puf.alert.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.alert.dto.MyAlertItem;
import com.mbc.puf.alert.entity.Alert;
import com.mbc.puf.alert.entity.AlertDelivery;
import com.mbc.puf.alert.repository.AlertDeliveryRepository;
import com.mbc.puf.alert.repository.AlertRepository;
import com.mbc.puf.car.entity.Car;
import com.mbc.puf.car.repository.CarRepository;
import com.mbc.puf.common.enums.AlertChannel;
import com.mbc.puf.common.enums.DevicePlatform;
import com.mbc.puf.common.enums.CameraRole;
import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.enums.UnderpassCurrentStatus;
import com.mbc.puf.common.enums.UnderpassEventType;
import com.mbc.puf.underpass.entity.Underpass;
import com.mbc.puf.underpass.entity.UnderpassEvent;
import com.mbc.puf.underpass.repository.UnderpassEventRepository;
import com.mbc.puf.common.enums.AlertType;
import com.mbc.puf.common.enums.DeliveryStatus;
import com.mbc.puf.member.entity.DeviceToken;
import com.mbc.puf.notification.dto.PushMessage;
import com.mbc.puf.notification.service.PushService;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.query.PagedQuery;
import com.mbc.puf.common.query.QueryConditions;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.notification.service.NotificationService;

import jakarta.persistence.EntityManager;

@Service
public class AlertServiceImpl implements AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertServiceImpl.class);

    // 관리자 목록: 계기 이벤트(지하차도) + 지역 범위 조건을 위해 underpass 까지 조인
    private static final String ADMIN_FROM = " from Alert a join UnderpassEvent ue on ue.id = a.eventId"
            + " join Underpass u on u.id = ue.underpassId";

    // 고객 "내 알림": 지하차도 이름 + 수신 전화번호까지
    private static final String MY_FROM = ADMIN_FROM + " join Customer c on c.id = a.customerId";

    private final AlertRepository alertRepository;
    private final AlertDeliveryRepository deliveryRepository;
    private final CarRepository carRepository;
    private final ScopeResolver scopeResolver;
    private final NotificationService notificationService;   // 고객 알림 센터(위험 분류)에도 남긴다
    private final EntityManager em;
    private final UnderpassEventRepository eventRepository;   // 선제 알림의 계기(위험도 상승) 이벤트 조회
    private final PushService pushService;                    // 앱·웹 푸시(FCM) 발송
    private final DbClock dbClock;
    private final int cooldownSeconds;                        // 같은 차량·같은 종류·같은 지하차도 알림 사이 최소 간격(0 = 제한 없음)

    public AlertServiceImpl(AlertRepository alertRepository, AlertDeliveryRepository deliveryRepository,
                            CarRepository carRepository, ScopeResolver scopeResolver,
                            NotificationService notificationService, EntityManager em,
                            UnderpassEventRepository eventRepository, PushService pushService,
                            DbClock dbClock, @Value("${puf.alert.cooldown-seconds:120}") int cooldownSeconds) {
        this.eventRepository = eventRepository;
        this.pushService = pushService;
        this.dbClock = dbClock;
        this.cooldownSeconds = cooldownSeconds;
        this.alertRepository = alertRepository;
        this.deliveryRepository = deliveryRepository;
        this.carRepository = carRepository;
        this.scopeResolver = scopeResolver;
        this.notificationService = notificationService;
        this.em = em;
    }

    @Override
    @Transactional
    public void sendPredictiveAlert(Long eventId, Long carId, String carNum) {
        carRepository.findById(carId)
                .ifPresent(car -> insert(eventId, car.getCustomerId(), carId, carNum, AlertType.PREDICTIVE));
    }

    @Override
    @Transactional
    public void sendRoiFinalAlert(Long eventId, Long carId, String carNum) {
        carRepository.findById(carId)
                .ifPresent(car -> insert(eventId, car.getCustomerId(), carId, carNum, AlertType.FINAL));
    }

    @Override
    @Transactional
    public void sendAlertsForPlate(Underpass underpass, CameraRole role, List<Car> cars, String carNum) {
        if (cars == null || cars.isEmpty()) {
            return;
        }
        boolean blocked = underpass.getCurrentStatus() == UnderpassCurrentStatus.BLOCKED
                && underpass.getCurrentBlockEventId() != null;
        if (role == CameraRole.APPROACH_LPR && (blocked || underpass.getCurrentRiskLevel().atLeast(RiskLevel.WATCH))) {
            // 차단 중이면 차단 이벤트(사유가 있음)를 계기로, 아니면 지금 단계로 올라간 가장 최근 상승 이벤트를 계기로 한다
            Long eventId = blocked ? underpass.getCurrentBlockEventId()
                    : eventRepository.findFirstByUnderpassIdAndEventTypeOrderByCreatedAtDescIdDesc(underpass.getId(),
                            UnderpassEventType.RISK_LEVEL_UP).map(UnderpassEvent::getId).orElse(null);
            if (eventId != null) {
                cars.forEach(car -> sendPredictiveAlert(eventId, car.getId(), carNum));
            }
        } else if (role == CameraRole.FINAL_LPR && blocked) {
            cars.forEach(car -> sendRoiFinalAlert(underpass.getCurrentBlockEventId(), car.getId(), carNum));
        }
    }

    private void insert(Long eventId, Long customerId, Long carId, String carNum, AlertType alertType) {
        if (inCooldown(eventId, carId, alertType)) {
            log.info("[alert] 쿨다운으로 건너뜀: carId={} type={} eventId={} ({}초 안에 같은 알림을 보냄)", carId, alertType, eventId,
                    cooldownSeconds);
            return;
        }
        Alert alert = new Alert();
        alert.setEventId(eventId);
        alert.setCustomerId(customerId);
        alert.setCarId(carId);
        alert.setCarNum(carNum);
        alert.setAlertType(alertType);
        alertRepository.saveAndFlush(alert);  // flush → id 확보

        // alert_delivery 채널 기록 (SMS: 실제 연동 전까지 PENDING)
        insertDelivery(alert.getId(), AlertChannel.SMS, null, DeliveryStatus.PENDING);

        // 고객 알림 센터(위험 분류): 계기 이벤트의 지하차도 이름과 함께 한 건 남긴다
        List<Object[]> info = em.createQuery("select u.name, ue.reason, ue.eventType from UnderpassEvent ue join Underpass u"
                        + " on u.id = ue.underpassId where ue.id = :id", Object[].class)
                .setParameter("id", eventId).getResultList();
        String underpassName = info.isEmpty() ? null : (String) info.get(0)[0];
        String reason = info.isEmpty() ? null : (String) info.get(0)[1];
        notificationService.notifyCustomerRisk(customerId, alert.getId(), underpassName, carNum, alertType, reason);

        // 푸시: 그 고객의 사용 중인 기기마다 발송 기록(PENDING)을 만들고, 커밋 뒤 FCM 으로 보내 결과(SUCCESS/FAIL)로 바꾼다
        List<DeviceToken> tokens = pushService.activeTokens(customerId);
        for (DeviceToken t : tokens) {
            insertDelivery(alert.getId(), t.getPlatform() == DevicePlatform.WEB ? AlertChannel.PUSH_WEB : AlertChannel.PUSH_APP,
                    t.getId(), DeliveryStatus.PENDING);
        }
        Long alertId = alert.getId();
        pushService.sendAfterCommit(tokens, new PushMessage(
                alertType == AlertType.FINAL ? "지하차도 진입 직전 알림" : "지하차도 접근 알림",
                (underpassName == null ? "" : underpassName + " · ") + carNum
                        + (reason == null || reason.isBlank() ? "" : " · 사유: " + reason),
                Map.of("category", "RISK", "refType", "ALERT", "refId", String.valueOf(alertId))),
                (t, status) -> deliveryRepository.updateStatus(alertId, t.getId(), status));

        // 실제 문자 발송 연동 전까지: 긴급재난문자 형식으로 보냈다고 치고 콘솔에 남긴다
        // (회원가입 없이도 번호판 매칭만으로 수신 가능하다는 컨셉이라 "앱 푸시"가 아니라 "문자"로 표기)
        log.info("\n===== [긴급재난문자 발송!!!] =====\n대상 고객ID: {}\n차량번호: {}\n" +
                "알림종류: {}\n계기 이벤트ID: {}\n==========================", customerId, carNum,
                alertType, eventId);
    }

    // 같은 차량에 같은 종류 알림을 같은 지하차도에서 방금 보냈으면 다시 보내지 않는다 (정체로 접근 카메라를 계속 지나는 경우 등)
    private boolean inCooldown(Long eventId, Long carId, AlertType alertType) {
        if (cooldownSeconds <= 0) {
            return false;
        }
        Long underpassId = em.createQuery("select ue.underpassId from UnderpassEvent ue where ue.id = :id", Long.class)
                .setParameter("id", eventId).getResultStream().findFirst().orElse(null);
        if (underpassId == null) {
            return false;
        }
        Long recent = em.createQuery("select count(a) from Alert a join UnderpassEvent ue on ue.id = a.eventId"
                        + " where a.carId = :carId and a.alertType = :type and ue.underpassId = :underpassId"
                        + " and a.sentAt > :since", Long.class)
                .setParameter("carId", carId).setParameter("type", alertType).setParameter("underpassId", underpassId)
                .setParameter("since", dbClock.now().minusSeconds(cooldownSeconds)).getSingleResult();
        return recent > 0;
    }

    private void insertDelivery(Long alertId, AlertChannel channel, Long tokenId, DeliveryStatus status) {
        AlertDelivery d = new AlertDelivery();
        d.setAlertId(alertId);
        d.setChannel(channel);
        d.setTokenId(tokenId);   // SMS면 null
        d.setStatus(status);
        deliveryRepository.save(d);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MyAlertItem> getMyAlerts(Long customerId, AlertType alertType, PeriodQuery period,
                                                 PageQuery page) {
        QueryConditions c = new QueryConditions()
                .add("a.customerId = ?", customerId)   // 본인 것만
                .eqIfPresent("a.alertType", alertType)
                .periodIfPresent("a.sentAt", period);

        return PagedQuery.fetch(em, "select a, u.name, c.phone, ue.eventType, ue.reason" + MY_FROM,
                "select count(a)" + MY_FROM, c,
                " order by a.sentAt desc, a.id desc", Object[].class, row -> {
                    Alert a = (Alert) row[0];
                    // 계기 이벤트가 차단이면 "통제 중", 그 사유가 있으면 문구에 같이 넣는다
                    boolean blocked = row[3] == UnderpassEventType.BLOCK;
                    return new MyAlertItem(a.getId(), (String) row[1], a.getCarNum(), (String) row[2],
                            a.getAlertType(), a.getSentAt(), blocked, (String) row[4]);
                }, page.toParams());
    }
}