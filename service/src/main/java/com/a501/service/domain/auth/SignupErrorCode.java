package com.a501.service.domain.auth;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SignupErrorCode implements ErrorCode {
    SIGNUP_PENDING(HttpStatus.CONFLICT, "SIGNUP_PENDING", "가입이 아직 완료되지 않았습니다."),
    TERMS_NOT_AGREED(HttpStatus.CONFLICT, "TERMS_NOT_AGREED", "필수 약관에 동의해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    SignupErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
