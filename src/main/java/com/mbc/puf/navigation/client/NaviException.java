package com.mbc.puf.navigation.client;

/** 길찾기 결과를 못 받았을 때(경로 없음, 응답 해석 실패 등). 컨트롤러 단계에서 BusinessException 으로 바뀐다 */
public class NaviException extends RuntimeException {

    public NaviException(String message) {
        super(message);
    }
}
