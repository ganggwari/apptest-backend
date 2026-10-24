package com.mbc.puf.notification.dto;

import java.util.Map;

/**
 * 푸시 한 통의 내용. data 의 값은 전부 문자열이어야 한다 (FCM 규칙).
 * 프런트는 data 의 refType / refId 로 알림을 눌렀을 때 이동할 화면을 정한다.
 */
public record PushMessage(String title, String body, Map<String, String> data) {

    public PushMessage {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
