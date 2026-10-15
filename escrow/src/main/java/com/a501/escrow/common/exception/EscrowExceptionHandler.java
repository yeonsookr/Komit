package com.a501.escrow.common.exception;

import com.a501.escrow.common.api.EscrowErrorResponse;
import com.a501.escrow.common.api.EscrowHeaderFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class EscrowExceptionHandler {

    private final EscrowHeaderFactory headerFactory;

    public EscrowExceptionHandler(EscrowHeaderFactory headerFactory) {
        this.headerFactory = headerFactory;
    }

    @ExceptionHandler(EscrowApiException.class)
    public ResponseEntity<EscrowErrorResponse> handleEscrowApiException(EscrowApiException exception) {
        return ResponseEntity.status(exception.status()).body(new EscrowErrorResponse(
                headerFactory.error(
                        exception.responseCode(),
                        exception.getMessage(),
                        exception.requestHeader())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<EscrowErrorResponse> handleUnreadableMessage() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new EscrowErrorResponse(
                headerFactory.error(
                        "Q1001",
                        "요청 본문의 형식이 잘못되었습니다. JSON 형식 또는 데이터 타입을 확인해 주세요.",
                        null)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<EscrowErrorResponse> handleInvalidArgument(
            MethodArgumentNotValidException exception
    ) {
        boolean invalidHeader = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .anyMatch(field -> field.equals("Header") || field.equals("header"));
        String code = invalidHeader ? "H1000" : "Q1001";
        String message = invalidHeader
                ? "HEADER 정보가 유효하지 않습니다."
                : "요청 본문의 형식이 잘못되었습니다. JSON 형식 또는 데이터 타입을 확인해 주세요.";
        return ResponseEntity.badRequest().body(new EscrowErrorResponse(
                headerFactory.error(code, message, null)));
    }
}
