package com.mbc.puf.notification.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mbc.puf.common.enums.CustomerNotificationCategory;

/** 고객 알림 센터 목록의 한 항목 */
public class CustomerNotificationItem {

    private final Long id;
    private final CustomerNotificationCategory category;
    private final String title;
    private final String body;
    private final String refType;   // ALERT / NOTICE / INQUIRY
    private final Long refId;
    private final boolean read;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private final LocalDateTime createdAt;

    public CustomerNotificationItem(Long id, CustomerNotificationCategory category, String title, String body,
                                    String refType, Long refId, boolean read, LocalDateTime createdAt) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.body = body;
        this.refType = refType;
        this.refId = refId;
        this.read = read;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public CustomerNotificationCategory getCategory() { return category; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getRefType() { return refType; }
    public Long getRefId() { return refId; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
