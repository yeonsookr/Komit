package com.a501.service.domain.developer.dto.request;

import java.util.Arrays;

import org.springframework.data.domain.Sort;

/**
 * 개발자 찾기 {@code sort} 파라미터. 목 명세
 * {@code Literal["devRating,desc", "explanatoryRating,desc", "price,asc", "contract,desc"]}에 대응합니다.
 *
 * <p>계약 값에 쉼표가 있어 자바 enum 상수 이름으로 쓸 수 없으므로 상수 이름과 계약 값을 분리하고, 문자열 → enum 변환은
 * {@link com.a501.service.domain.developer.controller.DeveloperSortEditor}가 맡습니다. 상수 이름({@code DEV_RATING_DESC})은
 * 계약 값이 아니므로 요청으로 받지 않습니다.
 *
 * <p>{@link #toString()}이 계약 값을 돌려주는 것은 계약의 일부입니다. 전역 예외 처리기가 상수의 {@code toString()}을 나열해
 * 허용 목록 문구를 만듭니다.
 *
 * <p>세 정렬은 개발자 도메인의 컬럼(평점, 가격표 최저가)으로 DB에서 정렬하고, {@link #CONTRACT_DESC}만 다른 도메인(계약·방)의
 * 집계가 필요해 서비스가 따로 처리합니다. 그래서 {@link #toSort()}는 계약 정렬에서 비어 있습니다.
 */
public enum DeveloperSort {
    /** 실력(개발력 평점) 높은 순. 파라미터를 생략했을 때의 기본값입니다. 평점이 없는 개발자는 뒤로 갑니다. */
    DEV_RATING_DESC("devRating,desc", Sort.by(Sort.Direction.DESC, "devRating")),
    /** 설명력 평점 높은 순. 평점이 없는 개발자는 뒤로 갑니다. */
    EXPLANATORY_RATING_DESC("explanatoryRating,desc", Sort.by(Sort.Direction.DESC, "explanatoryRating")),
    /** 비용 낮은 순. 컨설팅·개발을 가리지 않은 가격표 전체의 최저가로 견주며, 가격표가 없는 개발자는 뒤로 갑니다. */
    PRICE_ASC("price,asc", Sort.by(Sort.Direction.ASC, "price")),
    /** 마친 계약 많은 순. 계약 수는 다른 도메인의 집계라 DB 정렬이 아니라 서비스가 정렬합니다. */
    CONTRACT_DESC("contract,desc", Sort.unsorted());

    /** DB 정렬에 쓰는 속성 이름. {@code DeveloperRepositoryImpl}이 이 이름을 컬럼으로 바꿉니다. */
    public static final String PROPERTY_DEV_RATING = "devRating";
    public static final String PROPERTY_EXPLANATORY_RATING = "explanatoryRating";
    public static final String PROPERTY_PRICE = "price";

    private final String value;
    private final Sort sort;

    DeveloperSort(String value, Sort sort) {
        this.value = value;
        this.sort = sort;
    }

    /**
     * 계약 값으로 상수를 찾습니다.
     *
     * @throws IllegalArgumentException 허용되지 않은 값. 에디터가 이 예외를 타입 불일치로 넘겨 422가 됩니다.
     */
    public static DeveloperSort fromValue(String value) {
        return Arrays.stream(values())
                .filter(sort -> sort.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported sort: " + value));
    }

    /** Spring Data 정렬 조건. {@link #CONTRACT_DESC}는 DB 정렬이 아니라 비어 있습니다. */
    public Sort toSort() {
        return sort;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
