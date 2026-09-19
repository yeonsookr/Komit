package com.a501.service.common.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
    INVALID_REQUEST(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REQUEST", "요청 형식이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    /** 컨트롤러 앞 서블릿 단계에서 멀티파트 용량 제한에 걸린 경우. 목 명세 POST /api/uploads의 400과 같습니다. */
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "파일이 너무 큽니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CommonErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
