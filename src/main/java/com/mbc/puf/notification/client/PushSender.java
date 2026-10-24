package com.mbc.puf.notification.client;

import com.mbc.puf.notification.dto.PushMessage;
import com.mbc.puf.notification.dto.PushResult;

/** 푸시 한 통을 실제로 보내는 곳. 설정(puf.fcm.enabled)에 따라 FCM 또는 로그만 남기는 구현이 쓰인다 */
public interface PushSender {

    /** 토큰 하나에 보낸다. 예외를 던지지 않고 결과로 돌려준다 */
    PushResult send(String token, PushMessage message);
}
