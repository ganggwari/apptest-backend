package com.mbc.puf.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 업무 규칙 위반 시 서비스에서 던지는 공통 예외.
 * RuntimeException 이라서 던지면 @Transactional 이 자동으로 롤백한다.
 * HTTP 응답으로의 변환은 Controller 단계에서 만들 예외 처리기가 담당한다.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
