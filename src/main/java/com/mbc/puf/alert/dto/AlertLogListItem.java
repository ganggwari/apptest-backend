package com.mbc.puf.alert.dto;

import java.time.LocalDateTime;

import com.mbc.puf.common.enums.AlertType;

/** alert_log 목록 조회용. 이름 조인 없이 id들만 (발송 로직이 아직 없어 "실제로 쌓이는지" 확인용) */
public class AlertLogListItem {

    private final Long id;
    private final Long eventId;
    private final Long underpassId;
    private final Long customerId;
    private final Long carId;
    private final String carNum;
    private final AlertType alertType;
    private final LocalDateTime sentAt;
    private final String pushStatus;   // SUCCESS(받음) / FAIL(발송 실패) / PENDING(발송 중) / NO_PUSH(보낼 기기 없음·푸시 꺼 둠)

    public AlertLogListItem(Long id, Long eventId, Long underpassId, Long customerId, Long carId, String carNum,
                            AlertType alertType, LocalDateTime sentAt) {
        this(id, eventId, underpassId, customerId, carId, carNum, alertType, sentAt, null);
    }

    public AlertLogListItem(Long id, Long eventId, Long underpassId, Long customerId, Long carId, String carNum,
                            AlertType alertType, LocalDateTime sentAt, String pushStatus) {
        this.id = id;
        this.eventId = eventId;
        this.underpassId = underpassId;
        this.customerId = customerId;
        this.carId = carId;
        this.carNum = carNum;
        this.alertType = alertType;
        this.sentAt = sentAt;
        this.pushStatus = pushStatus;
    }

    public AlertLogListItem withPushStatus(String status) {
        return new AlertLogListItem(id, eventId, underpassId, customerId, carId, carNum, alertType, sentAt, status);
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public Long getUnderpassId() { return underpassId; }
    public Long getCustomerId() { return customerId; }
    public Long getCarId() { return carId; }
    public String getCarNum() { return carNum; }
    public AlertType getAlertType() { return alertType; }
    public LocalDateTime getSentAt() { return sentAt; }
    public String getPushStatus() { return pushStatus; }
}
