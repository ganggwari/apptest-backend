package com.mbc.puf.common.enums;

/** 통제 사유 프리셋 구분 (DB: reason_type). 차단할 때 쓰는 사유와 해제할 때 쓰는 사유를 나눈다 */
public enum ReasonType {
    BLOCK,     // 차단 사유
    RELEASE    // 해제 사유
}
