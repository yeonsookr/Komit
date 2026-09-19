package com.a501.escrow.common.security;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.a501.escrow.common.exception.EscrowApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class EscrowHeaderValidator {

    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("HHmmss")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern TRANSACTION_NO = Pattern.compile("\\d{20}");

    public void validate(EscrowRequestHeader header, String apiName, ApiRequirements requirements) {
        if (header == null) {
            fail("H1000", "HEADER 정보가 유효하지 않습니다.", null);
        }
        validateApiIdentity(header, apiName);
        validateTransmissionTime(header);
        validateInstitution(header);
        validateTransactionNumber(header);
        validateCredentials(header, requirements);
    }

    private static void validateApiIdentity(EscrowRequestHeader header, String apiName) {
        if (!apiName.equals(header.apiName())) {
            fail("H1001", "API 이름이 유효하지 않습니다.", header);
        }
        if (!header.apiName().equals(header.apiServiceCode())) {
            fail("H1006", "API 서비스코드가 유효하지 않습니다.", header);
        }
    }

    private static void validateTransmissionTime(EscrowRequestHeader header) {
        if (!validDate(header.transmissionDate())) {
            fail("H1002", "전송일자 형식이 유효하지 않습니다.", header);
        }
        if (!validTime(header.transmissionTime())) {
            fail("H1003", "전송시각 형식이 유효하지 않습니다.", header);
        }
    }

    private static void validateInstitution(EscrowRequestHeader header) {
        if (!"00100".equals(header.institutionCode())) {
            fail("H1004", "기관코드가 유효하지 않습니다.", header);
        }
        if (!"001".equals(header.fintechAppNo())) {
            fail("H1005", "핀테크 앱 일련번호가 유효하지 않습니다.", header);
        }
    }

    private static void validateTransactionNumber(EscrowRequestHeader header) {
        if ("00000000000000000000".equals(header.institutionTransactionUniqueNo())) {
            fail("H1007", "기관거래고유번호가 중복된 값입니다.", header);
        }
        if (header.institutionTransactionUniqueNo() == null
                || !TRANSACTION_NO.matcher(header.institutionTransactionUniqueNo()).matches()) {
            fail("H1010", "기관거래고유번호가 유효하지 않습니다.", header);
        }
    }

    private static void validateCredentials(
            EscrowRequestHeader header,
            ApiRequirements requirements
    ) {
        if (blank(header.clientApiKey()) || header.clientApiKey().length() > 255) {
            fail("E5001", "CLIENT API KEY가 유효하지 않습니다.", header);
        }
        if (requirements.idempotencyKeyRequired()
                && (blank(header.idempotencyKey()) || header.idempotencyKey().length() > 255)) {
            fail("E5002", "멱등키가 필요합니다.", header);
        }
        if (requirements.userKeyRequired()
                && (blank(header.userKey()) || header.userKey().length() > 40)) {
            fail("H1009", "USER KEY가 유효하지 않습니다.", header);
        }
    }

    private static boolean validDate(String value) {
        try {
            LocalDate.parse(value, DATE);
            return value != null && value.length() == 8;
        } catch (DateTimeParseException | NullPointerException exception) {
            return false;
        }
    }

    private static boolean validTime(String value) {
        try {
            LocalTime.parse(value, TIME);
            return value != null && value.length() == 6;
        } catch (DateTimeParseException | NullPointerException exception) {
            return false;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void fail(String code, String message, EscrowRequestHeader header) {
        throw new EscrowApiException(HttpStatus.BAD_REQUEST, code, message, header);
    }
}
