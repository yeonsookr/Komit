package com.a501.service.domain.developer.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.time.SeoulTime;
import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.code.service.CodeQueryService;
import com.a501.service.domain.developer.DeveloperErrorCode;
import com.a501.service.domain.developer.dto.response.MyDeveloperProfileResponse;
import com.a501.service.domain.developer.dto.response.RatingResponse;
import com.a501.service.domain.developer.entity.Developer;
import com.a501.service.domain.developer.entity.DeveloperProfile;
import com.a501.service.domain.developer.repository.DeveloperProfileRepository;
import com.a501.service.domain.developer.repository.DeveloperRepository;
import com.a501.service.domain.review.dto.response.ReviewResponse;
import com.a501.service.domain.review.service.ReviewQueryService;
import com.a501.service.domain.user.dto.UserProfileView;
import com.a501.service.domain.user.service.UserService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 개발자 프로필 조회({@code GET /api/users/me/developer-profile}). 각 부분은 담당 서비스에 묻고 여기서 한 응답으로 합칩니다.
 *
 * <p>목 명세 {@code DEV_FIELD}·{@code TECH_CATEGORY} 코드 이름은 코드 도메인에, 리뷰는 리뷰 도메인에, 이름·사진은 사용자 도메인에 묻습니다.
 * 프로필 사진 주소는 직접 올린 사진이 있으면 파일 API가 붙을 때까지 null이고, 없으면 카카오 사진 주소입니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeveloperProfileQueryService {
    static final String DEV_FIELD = "DEV_FIELD";

    private final DeveloperRepository developerRepository;
    private final DeveloperProfileRepository developerProfileRepository;
    private final UserService userService;
    private final CodeQueryService codeQueryService;
    private final CareerService careerService;
    private final PortfolioItemService portfolioItemService;
    private final PriceCatalogService priceCatalogService;
    private final DeveloperTechCategoryService techCategoryService;
    private final ReviewQueryService reviewQueryService;

    /**
     * 토큰 주인의 프로필.
     *
     * @throws BusinessException {@link DeveloperErrorCode#NO_DEVELOPER_PROFILE} 개발자 행이나 프로필이 없을 때
     */
    public MyDeveloperProfileResponse getMine(Integer userId) {
        Developer developer = developerRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(DeveloperErrorCode.NO_DEVELOPER_PROFILE));
        DeveloperProfile profile = developerProfileRepository.findByDeveloperIdAndDeletedFalse(developer.getId())
                .orElseThrow(() -> new BusinessException(DeveloperErrorCode.NO_DEVELOPER_PROFILE));
        return assemble(developer, profile, userService.getProfile(userId), profile.getUpdatedAt());
    }

    /** 저장 직후 응답을 만들 때도 같은 조립을 씁니다. {@code updatedAt}은 호출하는 쪽이 정합니다. */
    MyDeveloperProfileResponse assemble(Developer developer, DeveloperProfile profile, UserProfileView user, LocalDateTime updatedAt) {
        Integer developerId = developer.getId();
        List<Integer> techCodeIds = techCategoryService.listCodeIds(developerId);
        List<Integer> codeIds = new ArrayList<>(techCodeIds);
        if (profile.getFieldCodeId() != null) {
            codeIds.add(profile.getFieldCodeId());
        }
        Map<Integer, CodeResponse> codes = codeQueryService.findCodes(codeIds);
        List<ReviewResponse> reviews = reviewQueryService.findByDeveloper(developerId);

        return new MyDeveloperProfileResponse(
                developerId,
                user.name(),
                user.profileImageFileId() == null ? user.kakaoProfileImageUrl() : null,
                profile.getIntro(),
                profile.getDescription(),
                profile.getYearsOfExperience(),
                profile.getFieldCodeId() == null ? null : codes.get(profile.getFieldCodeId()),
                profile.getActiveFrom(),
                profile.getActiveTo(),
                careerService.hasVerified(developerId),
                RatingResponse.of(developer.getDevRating(), developer.getDevReviewCount()),
                RatingResponse.of(developer.getExplanatoryRating(), developer.getExplanatoryReviewCount()),
                techCodeIds.stream().map(codes::get).filter(Objects::nonNull).toList(),
                careerService.listMine(developerId),
                portfolioItemService.listMine(developerId),
                priceCatalogService.list(developerId),
                reviews,
                (int) reviews.stream().filter(review -> review.reply() != null).count(),
                SeoulTime.of(updatedAt));
    }
}
