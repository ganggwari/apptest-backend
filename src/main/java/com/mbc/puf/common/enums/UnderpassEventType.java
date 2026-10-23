package com.mbc.puf.common.enums;

/** 지하차도 이벤트 종류 (DB: underpass_event_type) */
public enum UnderpassEventType {
    RISK_LEVEL_UP,    // 위험도 상승
    RISK_LEVEL_DOWN,  // 위험도 하강
    BLOCK,            // 차단
    RELEASE           // 해제
}