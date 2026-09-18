package com.a501.service.common.exception;

import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        List<ErrorResponse.FieldErrorResponse> errors = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> new ErrorResponse.FieldErrorResponse(error.getField(), validationReason(error)))
                .toList();
        return ResponseEntity.unprocessableEntity().body(ErrorResponse.invalid(errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableBody() {
        return ResponseEntity.unprocessableEntity().body(ErrorResponse.invalid(List.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unexpected request processing failure", exception);
        return ResponseEntity.internalServerError().body(ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR));
    }

    private String validationReason(FieldError error) {
        if ("NotBlank".equals(error.getCode()) || "NotNull".equals(error.getCode())) {
            return "필수 항목입니다.";
        }
        return error.getDefaultMessage() == null ? "올바른 값을 입력해 주세요." : error.getDefaultMessage();
    }
}
