package com.mbc.puf.stream;

/** SSE 이벤트를 누구에게 보낼지 정하는 범위 */
public enum StreamScope {
    /** 공개 이벤트: /api/stream 에 붙은 모든 사람(비로그인 포함) + 해당 지역을 담당하는 관리자 */
    PUBLIC,
    /** 특정 고객 1명 (그 고객의 모든 탭/기기) */
    CUSTOMER,
    /** 특정 관리자 1명 */
    MANAGER,
    /** 관리자 전용: regionId 가 null 이면 모든 관리자, 있으면 그 지역 담당 관리자만 */
    ADMIN
}
