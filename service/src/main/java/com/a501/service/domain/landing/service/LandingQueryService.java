package com.a501.service.domain.landing.service;

import com.a501.service.domain.developer.service.DeveloperQueryService;
import com.a501.service.domain.landing.dto.response.FeaturedDeveloperListResponse;
import com.a501.service.domain.landing.dto.response.FeaturedDeveloperResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 랜딩 화면이 쓰는 공개 조회. 사용자 데이터를 읽지 않아 로그인 없이 부를 수 있습니다.
 *
 * <p>추천 개발자를 고르는 기준은 규칙서에 없어 개발력 평점이 높은 순으로 둡니다. 평점이 없는 개발자는 뒤로 가고, 같으면 리뷰가 많은
 * 순입니다. 기준이 정해지면 {@code DeveloperRepositoryImpl#findFeatured}만 바꿉니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LandingQueryService {
    private final DeveloperQueryService developerQueryService;

    /** 랜딩의 '추천 개발자' 카드. 노출할 개발자가 없으면 빈 배열입니다. */
    public FeaturedDeveloperListResponse getFeaturedDevelopers(int size) {
        return new FeaturedDeveloperListResponse(developerQueryService.findFeatured(size).stream()
                .map(FeaturedDeveloperResponse::from)
                .toList());
    }
}
