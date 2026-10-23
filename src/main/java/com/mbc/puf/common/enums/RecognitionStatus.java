package com.mbc.puf.common.enums;

/** 번호판 인식 상태 (DB: recognition_status) */
public enum RecognitionStatus {
    SUCCESS,  // 성공
    LOW_CONFIDENCE,  // 신뢰도 낮음
    FAIL,  // 실패
    UNMATCHED   // 미매칭
}
