package com.mbc.puf.common.enums;

/** 알림 채널별 발송 결과 (테이블: alert_delivery.status, notice_delivery.status) */
public enum DeliveryStatus {
    PENDING,  // 발송 대기 (실제 발송 연동 전 mock 상태)
    SUCCESS,  // 발송 성공
    FAIL      // 발송 실패
}
