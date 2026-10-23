package com.mbc.puf.common.enums;

/** 알림 발송 채널 (테이블: alert_delivery.channel, notice_delivery.channel) */
public enum AlertChannel {
    PUSH_WEB,  // 웹 푸시
    PUSH_APP,  // 앱 푸시
    SMS        // 문자
}
