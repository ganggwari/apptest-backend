package com.mbc.puf.navigation.service;

/**
 * "내비가 피해야 할 위험지점을 지금 DB 기준으로 다시 계산해 달라"는 요청.
 * 위험도 변경·차단·해제·지하차도 수정/삭제가 커밋된 뒤에 HazardRefreshListener 가 받아서 바로 처리한다.
 */
public record HazardRefreshRequested() {
}
