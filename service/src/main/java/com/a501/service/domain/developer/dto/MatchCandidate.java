package com.a501.service.domain.developer.dto;

import java.util.Set;

/**
 * 추천 점수를 매길 개발자 하나. 다른 도메인에 엔티티를 넘기지 않기 위한 경계다. (BACKEND_CONVENTIONS 7.1)
 *
 * <p>점수 계산에 쓰는 값만 담는다. 이름·소개처럼 화면에 보여줄 것은 추천 결과를 그릴 때 따로 읽는다.
 */
public record MatchCandidate(
        Integer developerId,
        Set<Integer> techCodeIds,
        Double devRating,
        Double explanatoryRating,
        int reviewCount,
        Double yearsOfExperience,
        boolean hasVerifiedCareer) {
    public MatchCandidate {
        techCodeIds = techCodeIds == null ? Set.of() : Set.copyOf(techCodeIds);
    }
}
