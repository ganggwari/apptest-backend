package com.mbc.puf.alert.dto;

import com.mbc.puf.common.enums.AlertChannel;
import com.mbc.puf.common.enums.AlertType;
import com.mbc.puf.common.enums.DeliveryStatus;

/**
 * 알림 발송 기록 목록 공통 검색 조건 (전부 선택).
 *  customerId / customerName : 고객 (이름은 부분일치)
 *  alertType                 : 위험 알림 목록에서만 (PREDICTIVE/FINAL)
 *  cameraId                  : 위험 알림 목록에서만. 그 카메라가 인식해서 보낸 알림만
 *  noticeId                  : 공지 알림 목록에서만
 *  channel / status          : 그 채널 / 그 결과의 발송이 있는 알림만 (발송 단위 목록에서는 그 발송 한 건의 조건)
 *  received                  : 알림 단위 목록에서만. false 면 푸시가 한 번도 도착하지 못한 알림, true 면 도착한 알림
 */
public class AlertLogQuery {

    private Long customerId;
    private String customerName;
    private AlertType alertType;
    private Long cameraId;
    private Long noticeId;
    private AlertChannel channel;
    private DeliveryStatus status;
    private Boolean received;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public AlertType getAlertType() { return alertType; }
    public void setAlertType(AlertType alertType) { this.alertType = alertType; }
    public Long getCameraId() { return cameraId; }
    public void setCameraId(Long cameraId) { this.cameraId = cameraId; }
    public Long getNoticeId() { return noticeId; }
    public void setNoticeId(Long noticeId) { this.noticeId = noticeId; }
    public AlertChannel getChannel() { return channel; }
    public void setChannel(AlertChannel channel) { this.channel = channel; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public Boolean getReceived() { return received; }
    public void setReceived(Boolean received) { this.received = received; }
}
