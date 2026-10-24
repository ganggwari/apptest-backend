package com.mbc.puf.notification.dto;

/** 토큰 하나에 보낸 결과 */
public enum PushResult {
    SUCCESS,        // 발송 요청이 받아들여짐
    INVALID_TOKEN,  // 만료·삭제된 토큰 (다시 쓰면 안 되므로 비활성화 대상)
    FAIL            // 일시적 오류 등 그 밖의 실패
}
