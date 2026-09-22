package com.a501.service.domain.developer.dto;

import com.a501.service.domain.developer.entity.Developer;
import com.a501.service.domain.developer.entity.DeveloperProfile;
import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 개발자 한 명을 목록·상세 응답으로 조립하기 위한 조회 결과. 개발자 행과 프로필 행에서 화면에 필요한 컬럼만 담습니다.
 *
 * <p>QueryDSL 조회가 {@code Projections.constructor}로 이 생성자를 직접 부르므로 필드 순서를 바꾸면
 * {@link com.a501.service.domain.developer.repository.DeveloperRepositoryImpl}도 함께 고쳐야 합니다.
 * 이름·사진·코드 이름·계약 수처럼 다른 도메인에 있는 값은 여기 없고, 조립 서비스가 각 도메인 서비스에 물어 채웁니다.
 */
public record DeveloperSearchRow(
        Integer developerId,
        Integer userId,
        String intro,
        Integer fieldCodeId,
        BigDecimal yearsOfExperience,
        String description,
        LocalTime activeFrom,
        LocalTime activeTo,
        Double devRating,
        Integer devReviewCount,
        Double explanatoryRating,
        Integer explanatoryReviewCount) {

    /** 엔티티 두 개에서 만듭니다. 저장 직후처럼 이미 엔티티를 들고 있을 때 씁니다. */
    public static DeveloperSearchRow of(Developer developer, DeveloperProfile profile) {
        return new DeveloperSearchRow(
                developer.getId(),
                developer.getUserId(),
                profile.getIntro(),
                profile.getFieldCodeId(),
                profile.getYearsOfExperience(),
                profile.getDescription(),
                profile.getActiveFrom(),
                profile.getActiveTo(),
                developer.getDevRating(),
                developer.getDevReviewCount(),
                developer.getExplanatoryRating(),
                developer.getExplanatoryReviewCount());
    }
}
