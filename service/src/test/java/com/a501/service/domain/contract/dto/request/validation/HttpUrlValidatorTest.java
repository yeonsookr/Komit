package com.a501.service.domain.contract.dto.request.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 목의 {@code urlparse} 검사와 같은 주소를 받고 거르는지 본다. 목 테스트 {@code test_url_must_be_http_or_https_with_host} 와 같은 사례다. */
class HttpUrlValidatorTest {
    private final HttpUrlValidator validator = new HttpUrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost:3000",
        "https://demo.example.com/path?q=1",
        "HTTPS://Demo.Example.com",
        "https://user@demo.example.com:8443/a b",
        "https://[::1]:8080/",
    })
    void acceptsHttpUrlsWithHost(String url) {
        assertThat(validator.isValid(url, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ftp://demo.example.com",
        "javascript:alert(1)",
        "demo.example.com",
        "https://",
        "https://:8080",
        "https:demo.example.com",
    })
    void rejectsOtherSchemesOrMissingHost(String url) {
        assertThat(validator.isValid(url, null)).isFalse();
    }
}
