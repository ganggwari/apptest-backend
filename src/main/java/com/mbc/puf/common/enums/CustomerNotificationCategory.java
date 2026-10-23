package com.mbc.puf.common.enums;

/** 고객 알림 분류 (DB: customer_notification_category) */
public enum CustomerNotificationCategory {
    RISK,      // 위험 (지하차도 접근·진입 직전 알림)
    NOTICE,    // 공지 (새 공지)
    INQUIRY    // 문의 (내 문의에 답변이 달림)
}