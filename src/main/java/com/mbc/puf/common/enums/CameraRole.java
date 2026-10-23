package com.mbc.puf.common.enums;

/** 카메라 역할 (DB: camera_role) */
public enum CameraRole {
    CCTV,  // 일반 CCTV
    APPROACH_LPR,  // 접근 길목 번호판 인식
    FINAL_LPR   // 진입 직전 최종 확인
}
