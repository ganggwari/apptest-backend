package com.mbc.puf.common.enums;

/** 관리자 계정 상태 (DB: manager_status) */
public enum ManagerStatus {
    PENDING,  // 가입 대기
    ACTIVE,  // 활성화
    WITHDRAWN   // 탈퇴
}
