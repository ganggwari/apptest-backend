package com.mbc.puf.notification.client;

import com.mbc.puf.notification.dto.PushMessage;
import com.mbc.puf.notification.dto.PushResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** puf.fcm.enabled=false (기본) 일 때: 실제로 보내지 않고 로그만 남긴다. 키 없이도 개발·시연이 돌아가게 한다 */
@Component
@ConditionalOnProperty(name = "puf.fcm.enabled", havingValue = "false", matchIfMissing = true)
public class LogPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LogPushSender.class);

    @Override
    public PushResult send(String token, PushMessage message) {
        String shown = token.length() > 12 ? token.substring(0, 12) + "..." : token;
        log.info("[push:stub] token={} title={} body={} data={}", shown, message.title(), message.body(), message.data());
        return PushResult.SUCCESS;
    }
}
