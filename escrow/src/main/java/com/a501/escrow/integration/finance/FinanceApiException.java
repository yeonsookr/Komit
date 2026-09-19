package com.a501.escrow.integration.finance;

public class FinanceApiException extends RuntimeException {
    private final int httpStatus;
    private final String code;

    public FinanceApiException(int httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }
}
