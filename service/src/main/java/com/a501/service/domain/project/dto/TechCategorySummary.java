package com.a501.service.domain.project.dto;

/** 프로젝트가 쓰는 기술 한 줄. 다른 도메인에 건네는 불변 값이다. */
public record TechCategorySummary(Integer techCodeId, String codeName) {}
