package com.mbc.puf.common.response;

/**
 * 모든 API 응답의 공통 틀.
 *   성공: {"success": true,  "message": "OK",      "data": ...}
 *   실패: {"success": false, "message": "사유",    "data": null}
 * 실패여도 HTTP 상태코드(400/404/409/500 등)는 그대로 내려보낸다.
 */
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;

    private ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
