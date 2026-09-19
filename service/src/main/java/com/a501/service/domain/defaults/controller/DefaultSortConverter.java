package com.a501.service.domain.defaults.controller;

import com.a501.service.domain.defaults.dto.request.DefaultSort;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * 쿼리 파라미터 문자열 {@code "id,asc"}를 {@link DefaultSort}로 바꾸는 변환기.
 *
 * <p>Spring MVC의 기본 enum 변환은 상수 이름({@code ID_ASC})만 받아들이므로 계약 값을 받으려면 변환기가 필요합니다.
 * {@code Converter} 빈은 Spring Boot가 MVC 변환 서비스에 자동으로 등록합니다.
 *
 * <p>허용되지 않은 값에서 던지는 {@link IllegalArgumentException}은 Spring이 타입 불일치로 감싸고, 전역 예외 처리기가
 * 422 {@code INVALID_REQUEST}에 {@code {field: "sort", reason: "'id,asc' or 'id,desc' 중 하나여야 합니다."}}로 바꿉니다.
 * 여기서 직접 예외를 만들거나 응답을 쓰지 않습니다.
 */
@Component
public class DefaultSortConverter implements Converter<String, DefaultSort> {

    @Override
    public DefaultSort convert(String source) {
        return DefaultSort.fromValue(source);
    }
}
