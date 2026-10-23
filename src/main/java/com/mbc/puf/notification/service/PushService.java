package com.mbc.puf.notification.service;

/**
 * 고객 푸시(FCM) 발송. 앱(APP)과 웹(WEB)에서 등록한 토큰으로 보낸다.
 * 푸시는 SSE 알림을 대신하는 게 아니라 "앱이 닫혀 있을 때도 알려 주는" 보조 수단이다.
 * FCM 설정(서비스 계정)이 없으면 아무것도 보내지 않고 조용히 넘어간다 (알림 저장·SSE 는 그대로 동작).
 */
public interface PushService {

    /** FCM 설정이 되어 있어 실제로 보낼 수 있는지 */
    boolean isEnabled();

    /** 한 고객의 등록된(사용 중인) 모든 기기로 보낸다. 트랜잭션 안에서 불러도 되며, 커밋된 뒤에 별도 스레드에서 보낸다 */
    void sendToCustomer(Long customerId, String title, String body, String refType, Long refId);

    /** 사용 중인 모든 기기로 보낸다 (전체 공지용) */
    void sendToAllCustomers(String title, String body, String refType, Long refId);
}
