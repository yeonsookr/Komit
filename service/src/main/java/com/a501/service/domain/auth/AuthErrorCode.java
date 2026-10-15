package com.a501.service.domain.auth;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
    KAKAO_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "KAKAO_AUTH_FAILED", "로그인하지 못했습니다. 다시 시도해 주세요."),
    KAKAO_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "KAKAO_UNAVAILABLE", "카카오 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "로그인이 만료되었습니다. 다시 로그인해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    AuthErrorCode(HttpStatus status, String code, String message) {
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
