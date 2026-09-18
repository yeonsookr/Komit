package com.a501.service.common.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    HttpStatus status();

    String code();

    String message();
}
