package com.mbc.puf.notification.dto;

import java.util.Map;

/** 안 읽은 알림 수. total 은 종 아이콘 숫자, byCategory 는 탭별 숫자 (분류 값은 0이어도 항상 포함) */
public class NotificationUnreadResponse {

    private final long total;
    private final Map<String, Long> byCategory;

    public NotificationUnreadResponse(long total, Map<String, Long> byCategory) {
        this.total = total;
        this.byCategory = byCategory;
    }

    public long getTotal() { return total; }
    public Map<String, Long> getByCategory() { return byCategory; }
}
