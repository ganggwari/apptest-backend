package com.mbc.puf.member.dto;

/**
 * 인증코드 발송 응답. 프런트 타이머용으로 "응답을 받은 시점부터 남은 초"를 준다.
 * (절대 시각을 주면 사용자 PC 시계가 어긋날 때 타이머가 틀어지기 때문)
 *
 * @param expiresInSeconds  코드를 입력할 수 있는 남은 시간(초). 0이 되면 재발송이 필요하다
 * @param resendAfterSeconds 재발송 버튼을 다시 누를 수 있게 되기까지 남은 시간(초)
 */
public record PhoneCodeTiming(int expiresInSeconds, int resendAfterSeconds) {
}
