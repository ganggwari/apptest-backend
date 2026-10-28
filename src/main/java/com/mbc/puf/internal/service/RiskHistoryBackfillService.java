package com.mbc.puf.internal.service;

/**
 * 지하차도 위험점수 과거 이력(risk_score)을 시연용으로 채운다.
 * 현재 점수 캐시(underpass.risk_score), SSE, 알림, risk_forecast 는 건드리지 않고 risk_score 에 과거 행만 넣는다.
 */
public interface RiskHistoryBackfillService {

    /**
     * 지금부터 hours 시간 전까지 stepMinutes 분 간격으로 과거 점수를 넣는다.
     * 이미 hours 시간치 이력이 있는 지하차도는 건너뛰고, 이미 쌓인 첫 점수보다 앞쪽만 채우므로 여러 번 불러도 안전하다.
     *
     * @return 새로 넣은 행 수
     */
    int backfill(int hours, int stepMinutes, double calmBase);
}
