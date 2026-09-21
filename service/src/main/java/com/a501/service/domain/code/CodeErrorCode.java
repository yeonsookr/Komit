package com.a501.service.domain.code;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CodeErrorCode implements ErrorCode {
    CODE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "CODE_GROUP_NOT_FOUND", "코드 분류를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CodeErrorCode(HttpStatus status, String code, String message) {
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
