package com.a501.service.common.exception;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, List<FieldErrorResponse> errors) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.code(), errorCode.message(), null);
    }

    public static ErrorResponse invalid(List<FieldErrorResponse> errors) {
        return new ErrorResponse(
                CommonErrorCode.INVALID_REQUEST.code(),
                CommonErrorCode.INVALID_REQUEST.message(),
                errors);
    }

    public record FieldErrorResponse(String field, String reason) {
    }
}
