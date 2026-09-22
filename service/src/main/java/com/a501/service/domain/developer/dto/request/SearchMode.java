package com.a501.service.domain.developer.dto.request;

import java.util.Arrays;

/**
 * 개발자 찾기의 검색 방식. 목 명세 {@code Literal["problem", "name"]}에 대응합니다.
 *
 * <p>계약 값이 소문자라 자바 enum 상수 이름과 다르므로 상수 이름과 계약 값을 분리하고, 문자열 → enum 변환은
 * {@link com.a501.service.domain.developer.controller.SearchModeEditor}가 맡습니다. 상수 이름({@code PROBLEM})은 계약 값이
 * 아니므로 요청으로 받지 않습니다.
 *
 * <p>{@link #toString()}이 계약 값을 돌려주는 것은 계약의 일부입니다. 허용되지 않은 값이 오면 전역 예외 처리기가 상수의
 * {@code toString()}을 나열해 {@code 'problem' or 'name' 중 하나여야 합니다.}라는 목과 같은 문구를 만듭니다.
 */
public enum SearchMode {
    /** 겪고 있는 문제를 문장으로 적어 찾습니다. 파라미터를 생략했을 때의 기본값입니다. */
    PROBLEM("problem"),
    /** 개발자 이름으로 찾습니다. */
    NAME("name");

    private final String value;

    SearchMode(String value) {
        this.value = value;
    }

    /**
     * 계약 값으로 상수를 찾습니다.
     *
     * @throws IllegalArgumentException 허용되지 않은 값. 에디터가 이 예외를 타입 불일치로 넘겨 422가 됩니다.
     */
    public static SearchMode fromValue(String value) {
        return Arrays.stream(values())
                .filter(mode -> mode.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported searchMode: " + value));
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
