package com.a501.escrow.common.exception;

import com.a501.escrow.common.api.EscrowRequestHeader;
import org.springframework.http.HttpStatus;

public class EscrowApiException extends RuntimeException {

    private final HttpStatus status;
    private final String responseCode;
    private final EscrowRequestHeader requestHeader;

    public EscrowApiException(
            HttpStatus status,
            String responseCode,
            String message,
            EscrowRequestHeader requestHeader
    ) {
        super(message);
        this.status = status;
        this.responseCode = responseCode;
        this.requestHeader = requestHeader;
    }

    public HttpStatus status() {
        return status;
    }

    public String responseCode() {
        return responseCode;
    }

    public EscrowRequestHeader requestHeader() {
        return requestHeader;
    }
}
