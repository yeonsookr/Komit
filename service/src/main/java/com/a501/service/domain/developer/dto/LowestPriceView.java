package com.a501.service.domain.developer.dto;

/** 개발자·작업 종류별 가격표 최저가를 DB에서 집계한 조회 결과. */
public interface LowestPriceView {
    Integer getDeveloperId();

    String getTaskType();

    Long getLowestPrice();
}
