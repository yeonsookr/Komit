package com.a501.service.domain.account;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AccountErrorCode implements ErrorCode {
    ACCOUNT_REJECTED(HttpStatus.BAD_REQUEST, "ACCOUNT_REJECTED", "은행과 계좌번호를 다시 확인해 주세요."),
    VERIFICATION_TOO_MANY(HttpStatus.BAD_REQUEST, "VERIFICATION_TOO_MANY", "같은 계좌로 너무 자주 요청했습니다. 잠시 뒤 다시 시도해 주세요."),
    VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND", "계좌 확인 건을 찾을 수 없습니다."),
    ESCROW_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ESCROW_UNAVAILABLE", "지금은 계좌를 확인할 수 없습니다. 잠시 뒤 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    AccountErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
