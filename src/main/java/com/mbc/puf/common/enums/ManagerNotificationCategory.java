package com.mbc.puf.common.enums;

/** 관리자 알림 분류 (DB: manager_notification_category). 화면의 "전체" 탭은 분류 없이 전부 보는 것이라 값이 없다 */
public enum ManagerNotificationCategory {
    RISK,       // 위험 (위험 단계 변화, 통제·해제)
    EQUIPMENT,  // 설비 (예지보전 이상)
    TASK,       // 업무 (수동 판별 대기, 문의 접수, 점검 대기 등 처리할 일)
    SYSTEM      // 시스템 (데이터 수신 지연·복구 등)
}