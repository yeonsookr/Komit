package com.a501.escrow.common.api;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class EscrowHeaderFactory {

    private static final String FALLBACK_DATE = "20260909";
    private static final String FALLBACK_TIME = "120000";
    private static final String FALLBACK_TRANSACTION_NO = "20260909120000000001";
    private static final Pattern DATE = Pattern.compile("[0-9]{8}");
    private static final Pattern TIME = Pattern.compile("[0-9]{6}");
    private static final Pattern TRANSACTION_NO = Pattern.compile("[0-9]{20}");

    public EscrowResponseHeader success(EscrowRequestHeader request) {
        return create("H0000", "정상처리 되었습니다.", request);
    }

    public EscrowResponseHeader error(String code, String message, EscrowRequestHeader request) {
        return create(code, message, request);
    }

    private EscrowResponseHeader create(String code, String message, EscrowRequestHeader request) {
        EscrowRequestHeader safe = request == null ? fallbackRequest() : request;
        String apiName = truncate(orFallback(safe.apiName(), "default"), 30);
        String apiServiceCode = truncate(orFallback(safe.apiServiceCode(), apiName), 30);
        return new EscrowResponseHeader(
                code,
                message,
                apiName,
                echo(safe.transmissionDate(), DATE, FALLBACK_DATE),
                echo(safe.transmissionTime(), TIME, FALLBACK_TIME),
                "00100",
                "001",
                apiServiceCode,
                echo(safe.institutionTransactionUniqueNo(), TRANSACTION_NO, FALLBACK_TRANSACTION_NO)
        );
    }

    private static EscrowRequestHeader fallbackRequest() {
        return new EscrowRequestHeader(
                "default", FALLBACK_DATE, FALLBACK_TIME, "00100", "001", "default",
                FALLBACK_TRANSACTION_NO, null, null, null);
    }

    private static String echo(String value, Pattern pattern, String fallback) {
        return value != null && pattern.matcher(value).matches() ? value : fallback;
    }

    private static String orFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }
}
