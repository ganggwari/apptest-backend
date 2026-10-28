package com.mbc.puf.display.service;

/**
 * "이 지하차도와 연결된 전광판 문구를 다시 맞춰 달라"는 요청.
 * 위험도 변경·차단·해제 같은 핵심 처리가 커밋된 뒤에 VmsReconcileListener 가 받아서 처리한다.
 *
 * @param eventId    계기가 된 차단/해제/위험도 이벤트(없으면 null) — 전광판 이력에 같이 남긴다
 * @param operatorId 수동 차단/해제를 한 관리자(자동이면 null)
 */
public record VmsReconcileRequested(Long underpassId, Long eventId, Long operatorId) {
}
