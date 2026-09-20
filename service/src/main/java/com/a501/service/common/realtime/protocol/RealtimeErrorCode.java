package com.a501.service.common.realtime.protocol;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum RealtimeErrorCode implements ErrorCode {
    UNSUPPORTED_PROTOCOL_VERSION(
            HttpStatus.BAD_REQUEST,
            "UNSUPPORTED_PROTOCOL_VERSION",
            "지원하지 않는 메시지 형식입니다. 새로고침해 주세요."),
    MALFORMED_ENVELOPE(
            HttpStatus.BAD_REQUEST, "MALFORMED_ENVELOPE", "메시지 형식이 올바르지 않습니다."),
    PAYLOAD_TOO_LARGE(
            HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", "메시지가 너무 큽니다."),
    RATE_LIMITED(
            HttpStatus.TOO_MANY_REQUESTS,
            "RATE_LIMITED",
            "메시지를 너무 빠르게 보내고 있습니다. 잠시 뒤 다시 시도해 주세요."),
    UNKNOWN_EVENT_TYPE(
            HttpStatus.BAD_REQUEST, "UNKNOWN_EVENT_TYPE", "처리할 수 없는 메시지입니다."),
    CHANNEL_FORBIDDEN(
            HttpStatus.FORBIDDEN, "CHANNEL_FORBIDDEN", "이 연결로는 다룰 수 없는 대상입니다."),
    RELAY_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "RELAY_UNAVAILABLE",
            "지금은 상대에게 전할 수 없습니다. 잠시 뒤에 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    RealtimeErrorCode(HttpStatus status, String code, String message) {
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
