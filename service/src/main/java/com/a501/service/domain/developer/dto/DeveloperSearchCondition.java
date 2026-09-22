package com.a501.service.domain.developer.dto;

import java.util.Collection;

/**
 * 개발자 찾기 조회 조건. 컨트롤러 파라미터를 서비스가 DB 조건으로 옮긴 것입니다.
 *
 * <p>{@code null}은 "그 조건을 걸지 않음"입니다. 이름 검색은 사용자 도메인에 이름으로 물어 받은 사용자 식별자 목록으로 바꿔
 * 넘깁니다. 다른 도메인의 엔티티를 조인하지 않기 위해서입니다. (BACKEND_CONVENTIONS 7.1)
 *
 * @param fieldCodeIds 분야 코드 식별자. 하나라도 맞는 개발자를 찾습니다. null이면 분야로 거르지 않습니다.
 * @param userIds      이름 검색에 맞은 사용자 식별자. null이면 이름으로 거르지 않습니다. 빈 목록은 서비스가 미리 걸러 여기 오지 않습니다.
 */
public record DeveloperSearchCondition(Collection<Integer> fieldCodeIds, Collection<Integer> userIds) {
    public static DeveloperSearchCondition none() {
        return new DeveloperSearchCondition(null, null);
    }
}
