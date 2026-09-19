package com.a501.service.domain.defaults.dto.request;

import java.util.Arrays;

import org.springframework.data.domain.Sort;

/**
 * 목록 조회 {@code sort} 파라미터. 목 명세 {@code Literal["id,asc", "id,desc"]}에 대응합니다.
 *
 * <p>계약 값에 쉼표가 있어 자바 enum 상수 이름으로 쓸 수 없으므로 상수 이름과 계약 값을 분리하고,
 * 문자열 → enum 변환은 {@link com.a501.service.domain.defaults.controller.DefaultSortConverter}가 맡습니다.
 *
 * <p>{@link #toString()}이 계약 값을 돌려주는 것은 계약의 일부입니다. 허용되지 않은 값이 오면 전역 예외 처리기가
 * enum 상수의 {@code toString()}을 나열해 {@code 'id,asc' or 'id,desc' 중 하나여야 합니다.}라는 목과 같은 문구를 만듭니다.
 */
public enum DefaultSort {
    /** id 오름차순. 파라미터를 생략했을 때의 기본값입니다. */
    ID_ASC("id,asc", Sort.Direction.ASC),
    /** id 내림차순. */
    ID_DESC("id,desc", Sort.Direction.DESC);

    /** 정렬에 쓸 수 있는 유일한 속성. 기준 API는 {@code id}만 지원합니다. */
    public static final String PROPERTY = "id";

    private final String value;
    private final Sort.Direction direction;

    DefaultSort(String value, Sort.Direction direction) {
        this.value = value;
        this.direction = direction;
    }

    /**
     * 계약 값으로 상수를 찾습니다.
     *
     * @param value 요청에 온 문자열. {@code "id,asc"} 또는 {@code "id,desc"}
     * @return 대응하는 상수
     * @throws IllegalArgumentException 허용되지 않은 값. 변환기가 이 예외를 타입 불일치로 넘겨 422가 됩니다.
     */
    public static DefaultSort fromValue(String value) {
        return Arrays.stream(values())
                .filter(sort -> sort.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported sort: " + value));
    }

    /**
     * Spring Data 정렬 조건으로 바꿉니다. 속성은 항상 {@value #PROPERTY}입니다.
     *
     * @return 이 상수에 해당하는 정렬
     */
    public Sort toSort() {
        return Sort.by(direction, PROPERTY);
    }

    /**
     * 계약에 쓰는 문자열 표기.
     *
     * @return {@code "id,asc"} 또는 {@code "id,desc"}
     */
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
