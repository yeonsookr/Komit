package com.a501.escrow.integration.finance;

public class FinanceClientUnavailableException extends RuntimeException {

    public FinanceClientUnavailableException(String message) {
        super(message);
    }

    public FinanceClientUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
