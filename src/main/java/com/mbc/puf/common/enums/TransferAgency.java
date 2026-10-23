package com.mbc.puf.common.enums;

/** 통제정보 이관 대상 기관 (테이블: ctrl_transfer.agency) */
public enum TransferAgency {
    POLICE,  // 경찰 (차단/해제 시 신호조정 협조 요청)
    FIRE     // 소방서 (잔류차량 구조 요청)
}
