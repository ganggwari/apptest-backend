package com.mbc.puf.common.sms;

/** 문자(SMS) 발송 추상화. 지금은 StubSmsSender(콘솔 출력만), 실제 문자 서비스 연동 시 구현체를 교체한다 */
public interface SmsSender {

    /**
     * @param phone   숫자만 있는 전화번호 (예: 01012345678)
     * @param message 보낼 내용
     */
    void send(String phone, String message);
}
