package com.mbc.puf.alert.entity;

import com.mbc.puf.common.enums.AlertChannel;
import com.mbc.puf.common.enums.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;

/** 개인화 알림 채널별 발송 시도 (테이블: alert_delivery) */
@Entity
@Table(name = "alert_delivery")
public class AlertDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private AlertChannel channel;

    @Column(name = "token_id")
    private Long tokenId;   // 푸시 채널일 때만 값 있음, SMS는 NULL

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeliveryStatus status;

    @Generated
    @Column(name = "sent_at", insertable = false, updatable = false)
    private LocalDateTime sentAt;

    public AlertDelivery() {}

    public Long getId() { return id; }

    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }

    public AlertChannel getChannel() { return channel; }
    public void setChannel(AlertChannel channel) { this.channel = channel; }

    public Long getTokenId() { return tokenId; }
    public void setTokenId(Long tokenId) { this.tokenId = tokenId; }

    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }

    public LocalDateTime getSentAt() { return sentAt; }
}
