package com.a501.service.domain.defaults.controller;

import java.beans.PropertyEditorSupport;

import com.a501.service.domain.defaults.dto.request.DefaultSort;

/**
 * 쿼리 파라미터 문자열 {@code "id,asc"}를 {@link DefaultSort}로 바꾸는 바인딩 에디터. {@code DefaultController}가
 * {@code @InitBinder}로 요청마다 등록합니다.
 *
 * <p><b>{@code Converter} 빈이 아니라 {@code PropertyEditor}인 이유.</b> Spring의 {@code TypeConverterDelegate}는
 * {@code ConversionService} 변환이 실패하면 enum에 한해 {@code Enum.valueOf(상수 이름)}으로 한 번 더 시도합니다.
 * {@code Converter}로 등록하면 계약 값 {@code "id,asc"}는 거절해도 상수 이름 {@code "ID_ASC"}가 그 폴백을 타고 통과해
 * 목(422)과 다른 200이 납니다. 커스텀 에디터가 등록되어 있으면 폴백이 실행되지 않으므로 계약 값만 받습니다.
 *
 * <p>허용되지 않은 값에서 던지는 {@link IllegalArgumentException}은 Spring이 {@code TypeMismatchException}으로 감싸고,
 * 전역 예외 처리기가 422 {@code INVALID_REQUEST}에 {@code {field: "sort", reason: "'id,asc' or 'id,desc' 중 하나여야 합니다."}}로
 * 바꿉니다. 여기서 직접 예외를 만들거나 응답을 쓰지 않습니다. 값을 다듬지도(trim) 않습니다. 목이 공백 섞인 값을 거절하므로
 * 실서버도 같아야 합니다.
 *
 * <p>에디터는 상태를 가지므로 빈으로 공유하지 않고 바인더마다 새로 만듭니다.
 */
public class DefaultSortEditor extends PropertyEditorSupport {

    /**
     * 계약 값을 enum으로 바꿉니다.
     *
     * @param text 요청에 온 문자열
     * @throws IllegalArgumentException 허용되지 않은 값
     */
    @Override
    public void setAsText(String text) {
        setValue(DefaultSort.fromValue(text));
    }

    /**
     * enum을 계약 값으로 되돌립니다. 바인딩 결과를 문자열로 보여 줄 때 상수 이름이 아니라 계약 값이 나오게 합니다.
     *
     * @return {@code "id,asc"} 또는 {@code "id,desc"}. 값이 없으면 빈 문자열
     */
    @Override
    public String getAsText() {
        return getValue() instanceof DefaultSort sort ? sort.value() : "";
    }
}
