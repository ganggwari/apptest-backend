package com.mbc.puf.notification.service;

import com.mbc.puf.notification.dto.PushMessage;

import java.util.List;

import com.mbc.puf.common.enums.DeliveryStatus;
import com.mbc.puf.member.entity.DeviceToken;

/**
 * 고객에게 푸시를 보낸다. 모든 발송은 "호출한 트랜잭션이 커밋된 뒤" 별도 스레드에서 이뤄지므로
 * 알림 저장이 실패하면 푸시도 나가지 않고, FCM 응답이 느려도 요청이 막히지 않는다.
 * 만료된 토큰(INVALID_TOKEN)은 자동으로 비활성화한다.
 */
public interface PushService {

    /** 토큰 하나의 결과를 받고 싶을 때 (예: alert_delivery 상태 갱신) */
    @FunctionalInterface
    interface ResultListener {
        void onResult(DeviceToken token, DeliveryStatus status);
    }

    /** 그 고객의 사용 중인 토큰 */
    List<DeviceToken> activeTokens(Long customerId);

    /** 주어진 토큰들에 보낸다. 커밋 후 비동기. listener 는 null 가능 */
    void sendAfterCommit(List<DeviceToken> tokens, PushMessage message, ResultListener listener);

    /** 그 고객의 모든 사용 중 토큰에 보낸다. 커밋 후 비동기 */
    void sendToCustomerAfterCommit(Long customerId, PushMessage message);

    /** 사용 중인 모든 토큰(전체 고객)에 보낸다 — 새 공지처럼 전체 대상일 때. 커밋 후 비동기 */
    void sendToAllAfterCommit(PushMessage message);

    /**
     * 관리자 기기에 보낸다. regionId 가 null 이면 모든 (활성) 관리자, 값이 있으면 그 지역 담당 관리자만.
     * 관리자 알림(manager_notification)을 만들 때 같이 부른다. 커밋 후 비동기
     */
    void sendToManagersAfterCommit(Long regionId, PushMessage message);

    /** 시험 발송: 지금 바로(동기) 보내고 토큰별 결과 개수를 돌려준다 */
    TestResult sendTest(Long customerId, PushMessage message);

    record TestResult(int tokenCount, int success, int invalid, int fail) {
    }
}
