package com.a501.service.domain.developer.controller;

import java.beans.PropertyEditorSupport;

import com.a501.service.domain.developer.dto.request.SearchMode;

/**
 * 쿼리 파라미터 문자열 {@code "problem"}·{@code "name"}을 {@link SearchMode}로 바꾸는 바인딩 에디터. {@code DeveloperController}가
 * {@code @InitBinder}로 요청마다 등록합니다.
 *
 * <p>{@code Converter} 빈이 아니라 {@code PropertyEditor}인 이유는 기준 구현
 * {@link com.a501.service.domain.defaults.controller.DefaultSortEditor}에 있습니다. 상수 이름({@code PROBLEM})이
 * {@code Enum.valueOf} 폴백으로 통과하지 않게 하고, 값을 다듬지(trim) 않아 목과 같이 공백 섞인 값을 거절합니다.
 */
public class SearchModeEditor extends PropertyEditorSupport {

    @Override
    public void setAsText(String text) {
        setValue(SearchMode.fromValue(text));
    }

    @Override
    public String getAsText() {
        return getValue() instanceof SearchMode mode ? mode.value() : "";
    }
}
