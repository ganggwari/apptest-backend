
package com.mbc.puf.common.exception;

import java.io.IOException;
import java.util.stream.Collectors;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.mbc.puf.common.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;

/** 모든 컨트롤러에서 발생한 예외를 한곳에서 받아 ApiResponse(JSON)로 변환한다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 서비스에서 던진 업무 규칙 위반 (404 / 409 / 400 ...)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.error(e.getMessage()));
    }

    // @Valid 검증 실패 (DTO에 붙인 message가 응답에 실린다)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        // 형식 변환 실패(예: startDate=2026-13-01, page=abc)는 스프링 원문 메시지 대신 항목 이름만 알려준다
        // (@ModelAttribute 로 받는 공통 조건 묶음 PageQuery/PeriodQuery 등에서 발생)
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.isBindingFailure()
                        ? "요청 파라미터 형식이 올바르지 않습니다. (" + f.getField() + ")"
                        : f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        if (message.isEmpty()) {
            message = "요청값이 올바르지 않습니다.";
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    // JSON 문법 오류, 잘못된 enum 값, 타입 불일치 등 요청 본문을 읽지 못한 경우
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e,
                                                               HttpServletRequest request) {
        // 어느 항목이 문제인지 알 수 있도록 Jackson 이 알려주는 필드 경로를 메시지에 붙인다 (예: heightM)
        String field = null;
        Throwable cause = e.getCause();
        if (cause instanceof tools.jackson.databind.exc.MismatchedInputException mie && !mie.getPath().isEmpty()) {
            field = mie.getPath().stream()
                    .map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining("."));
        }
        // 운영 중 원인을 바로 찾을 수 있게 어떤 요청이 왜 실패했는지 로그로 남긴다
        log.warn("요청 본문을 읽지 못했습니다: {} {} (field={}, cause={})",
                request.getMethod(),
                request.getRequestURI(),
                field,
                cause == null ? e.getMessage() : cause.getClass().getSimpleName());

        String message = field == null ? "요청 본문 형식이 올바르지 않습니다."
                : "요청 본문 형식이 올바르지 않습니다. (" + field + ")";
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    // DB 제약 위반(길이·범위 초과, 중복, 참조 오류 등).
    // 500 대신 원인을 알 수 있는 상태코드로 응답하고 원인은 로그에 남긴다.
    // 입력 검증(@Valid)에서 못 걸러낸 값이 여기로 온다
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e,
                                                                 HttpServletRequest request) {
        Throwable root = e.getMostSpecificCause();
        String detail = root.getMessage() == null ? "" : root.getMessage();

        log.warn("DB 제약 위반: {} {} - {}",
                request.getMethod(),
                request.getRequestURI(),
                detail);

        if (detail.contains("numeric field overflow")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("숫자 값이 허용 범위를 넘었습니다. 입력값을 확인해주세요."));
        }
        if (detail.contains("value too long")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("입력한 글자 수가 허용 범위를 넘었습니다. 입력값을 확인해주세요."));
        }
        if (detail.contains("duplicate key")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("이미 등록된 값입니다."));
        }
        if (detail.contains("foreign key")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("연결된 데이터가 있어 처리할 수 없습니다."));
        }

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("저장할 수 없는 값입니다. 입력값을 확인해주세요."));
    }

    // 경로/쿼리 파라미터 타입 오류 (예: /api/regions/abc)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("요청 파라미터 형식이 올바르지 않습니다. (" + e.getName() + ")"));
    }

    // @PreAuthorize 등 메서드 보안이 던진 예외.
    // 로그인하지 않은 상태면 401, 로그인은 했지만 권한이 부족하면 403.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e,
                                                                HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = authentication == null
                || authentication instanceof AnonymousAuthenticationToken;
        String who = anonymous ? "anonymous" : authentication.getName();

        if (anonymous) {
            log.warn("인증되지 않은 접근 차단: {} {} (principal={})",
                    request.getMethod(),
                    request.getRequestURI(),
                    who);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("인증이 필요합니다. 다시 로그인해주세요."));
        }

        log.warn("권한 부족으로 접근 차단: {} {} (principal={})",
                request.getMethod(),
                request.getRequestURI(),
                who);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("접근 권한이 없습니다."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("인증이 필요합니다. 다시 로그인해주세요."));
    }

    // 클라이언트가 연결을 끊은 경우.
    // HTTP 메서드와 요청 URL을 함께 기록하여
    // SSE 연결 종료인지 일반 REST API 연결 종료인지 구분한다.
    //
    // 이미 사용할 수 없는 응답이므로 추가 응답은 작성하지 않는다.
    @ExceptionHandler({
            ClientAbortException.class,
            AsyncRequestNotUsableException.class
    })
    public void handleClientAbort(Exception e, HttpServletRequest request) {
        log.warn(
                "클라이언트 연결 종료: {} {} (exception={}, message={})",
                request.getMethod(),
                request.getRequestURI(),
                e.getClass().getSimpleName(),
                e.getMessage()
        );
    }

    // 그 밖의 IO 오류(파일 처리 등) / 응답 직렬화 실패.
    @ExceptionHandler({
            IOException.class,
            HttpMessageNotWritableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleIOFailure(Exception e) {
        log.error("응답 처리 중 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 오류가 발생했습니다."));
    }

    // 그 밖의 모든 예외!
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        // Spring MVC 자체 예외(없는 URL 404, 허용 안 되는 메서드 405 등)는 원래 상태코드를 유지
        if (e instanceof ErrorResponse errorResponse) {
            String detail = errorResponse.getBody().getDetail();
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .body(ApiResponse.error(
                            detail != null ? detail : "요청을 처리할 수 없습니다."
                    ));
        }

        // 예상 못 한 오류는 원인을 로그에 남기고 사용자에게는 일반 메시지만 노출
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 오류가 발생했습니다."));
    }
}
