package com.a501.service.domain.consulting;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ConsultingErrorCode implements ErrorCode {
    /** 없는 세션이거나 그 세션의 당사자가 아니다. 존재를 알리지 않으려 둘을 같게 다룬다. */
    SESSION_NOT_FOUND(
            HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "세션을 찾을 수 없습니다."),
    SESSION_ENDED(
            HttpStatus.CONFLICT, "SESSION_ENDED", "이미 끝난 세션입니다."),
    CONSENT_REQUIRED(
            HttpStatus.CONFLICT,
            "CONSENT_REQUIRED",
            "상대가 아직 들어오지 않았습니다. 양쪽이 모두 동의해야 시작할 수 있습니다."),
    ENTRY_NOT_OPEN(
            HttpStatus.CONFLICT, "ENTRY_NOT_OPEN", "아직 입장할 수 있는 시각이 아닙니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ConsultingErrorCode(HttpStatus status, String code, String message) {
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
