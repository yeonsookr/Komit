package com.a501.service.common.exception;

import java.util.Objects;

/**
 * 형식은 맞지만 저장된 값과 맞춰 봐야 틀린 것을 아는 필드. 422 {@code INVALID_REQUEST} 에 그 필드 하나를 실어 보낸다.
 *
 * <p>Bean Validation 으로는 DB 를 볼 수 없어 서비스가 던진다. 목의 {@code _field_error} 와 같은 본문이다.
 */
public class InvalidFieldException extends RuntimeException {
    private final String field;
    private final String reason;

    public InvalidFieldException(String field, String reason) {
        super(field);
        this.field = Objects.requireNonNull(field, "field");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public String getField() {
        return field;
    }

    public String getReason() {
        return reason;
    }
}
