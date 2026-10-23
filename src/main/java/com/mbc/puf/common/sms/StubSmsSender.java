package com.mbc.puf.common.sms;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 개발·시연용 문자 발송기: 실제로 보내지 않고 콘솔에 내용만 찍는다.
 * 인증코드는 서버 콘솔 로그나 phone_verify 테이블(code 컬럼)에서 확인할 수 있고,
 * puf.sms-stub.expose-code=true 면 GET /api/dev/sms/last?phone=... 로도 볼 수 있다(DevSmsController).
 * 실제 문자 서비스를 붙일 때는 SmsSender 를 구현한 클래스를 새로 만들고 이 클래스는 삭제한다
 * (둘 다 @Component 면 어느 쪽을 쓸지 몰라 실행이 안 된다).
 */
@Component
public class StubSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(StubSmsSender.class);

    // 번호별 마지막으로 "보낸" 내용 (개발용 확인 API 에서만 읽는다)
    private final Map<String, String> lastMessages = new ConcurrentHashMap<>();

    @Override
    public void send(String phone, String message) {
        lastMessages.put(phone, message);
        log.info("\n===== [문자 발송(스텁)] =====\n받는 번호: {}\n내용: {}\n============================", phone, message);
    }

    public Optional<String> lastMessage(String phone) {
        return Optional.ofNullable(lastMessages.get(phone));
    }
}
