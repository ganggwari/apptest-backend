package com.mbc.puf.common.enums;

/** 수동 검토 상태 (DB: review_status) */
public enum ReviewStatus {
    NONE,  // 검토 불필요
    PENDING,  // 검토 대기
    CONFIRMED,  // 확인 완료
    AUTO_PROCESSED   // 자동 처리
}
