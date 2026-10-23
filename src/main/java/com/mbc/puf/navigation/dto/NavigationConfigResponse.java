package com.mbc.puf.navigation.dto;

/**
 * 내비 화면 공개 설정.
 *
 * @param kakaoJsKey 카카오 JavaScript 키 (도메인 제한 키만. REST 키는 절대 내려주지 않는다)
 */
public record NavigationConfigResponse(String kakaoJsKey) {
}
