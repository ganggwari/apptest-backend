package com.mbc.puf.stream;

/**
 * SSE 로 보낼 이벤트 한 건.
 *
 * @param name     이벤트 이름 (프런트의 addEventListener 이름과 같아야 함. 예: "risk-update")
 * @param data     JSON 으로 변환되어 전송될 내용
 * @param scope    받는 사람 범위
 * @param regionId 지역 필터 (null 이면 지역 상관없이 전체). PUBLIC/ADMIN 에서 관리자에게 보낼 때 쓰인다
 * @param targetId CUSTOMER/MANAGER 범위일 때 받는 사람의 id
 */
public record StreamEvent(String name, Object data, StreamScope scope, Long regionId, Long targetId) {
}
