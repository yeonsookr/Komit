package com.a501.service.domain.contract.dto.request.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link HttpUrl} 구현. 목은 Python {@code urlparse}로 스킴과 호스트만 본다. 여기서도 두 가지만 같은 방식으로 본다.
 * {@link java.net.URI}는 공백 같은 글자가 섞이면 스킴을 보기 전에 거부해 목과 결과가 달라지므로 쓰지 않는다.
 * 비었거나 없는 값은 {@code @NotNull}·{@code @Size} 가 따로 거른다.
 */
public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {
    /** 스킴 뒤 {@code //} 부터 경로·질의·조각 앞까지가 주소 부분이다. */
    private static final Pattern URL = Pattern.compile("^(?<scheme>[A-Za-z][A-Za-z0-9+.-]*)://(?<authority>[^/?#]*)");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        Matcher matcher = URL.matcher(value);
        if (!matcher.find()) {
            return false;
        }
        String scheme = matcher.group("scheme").toLowerCase(Locale.ROOT);
        return (scheme.equals("http") || scheme.equals("https")) && !host(matcher.group("authority")).isEmpty();
    }

    /** 사용자 정보와 포트를 떼어 낸 호스트. {@code https://:8080} 처럼 호스트가 비면 빈 문자열이다. */
    private static String host(String authority) {
        String hostAndPort = authority.substring(authority.lastIndexOf('@') + 1);
        if (hostAndPort.startsWith("[")) {
            int end = hostAndPort.indexOf(']');
            return end < 0 ? "" : hostAndPort.substring(1, end);
        }
        int colon = hostAndPort.indexOf(':');
        return colon < 0 ? hostAndPort : hostAndPort.substring(0, colon);
    }
}
