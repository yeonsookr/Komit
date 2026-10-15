package com.a501.service.domain.project.dto;

/** 마이페이지가 다른 도메인의 엔티티 없이 사용하는 내 프로젝트 최소 정보. */
public record MyProjectView(Integer projectId, String name) {
}
