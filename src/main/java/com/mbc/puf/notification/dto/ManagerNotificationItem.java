package com.mbc.puf.notification.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mbc.puf.common.enums.ManagerNotificationCategory;

/** 관리자 알림 센터 목록의 한 항목. read 는 "나"의 읽음 여부 */
public class ManagerNotificationItem {

    private final Long id;
    private final ManagerNotificationCategory category;
    private final Long regionId;   // 전체 관리자 대상이면 null
    private final String title;
    private final String body;
    private final String refType;
    private final Long refId;
    private final boolean read;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private final LocalDateTime createdAt;

    public ManagerNotificationItem(Long id, ManagerNotificationCategory category, Long regionId, String title,
                                   String body, String refType, Long refId, boolean read, LocalDateTime createdAt) {
        this.id = id;
        this.category = category;
        this.regionId = regionId;
        this.title = title;
        this.body = body;
        this.refType = refType;
        this.refId = refId;
        this.read = read;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public ManagerNotificationCategory getCategory() { return category; }
    public Long getRegionId() { return regionId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getRefType() { return refType; }
    public Long getRefId() { return refId; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
