package com.a501.service.domain.developer.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.code.service.CodeQueryService;
import com.a501.service.domain.developer.dto.request.WriteDeveloperProfileRequest;
import com.a501.service.domain.developer.entity.Developer;
import com.a501.service.domain.developer.entity.DeveloperProfile;
import com.a501.service.domain.developer.repository.DeveloperProfileRepository;
import com.a501.service.domain.developer.repository.DeveloperRepository;
import com.a501.service.domain.setting.service.SettingService;
import com.a501.service.domain.user.dto.UserProfileView;
import com.a501.service.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 개발자 프로필 저장({@code PUT /api/users/me/developer-profile}). 화면의 저장 버튼 하나가 이 요청 하나를 보내며
 * 소개·기술·경력·작업물·가격표를 통째로 받아 보낸 내용으로 맞춥니다.
 *
 * <p><b>프로필이 없으면 만듭니다.</b> 개발자 프로필을 만드는 경로는 이 하나뿐입니다. 만들 때 개발자 행이 생기고 계정에 개발자 역할이
 * 더해지며(R-ACC-08) 판매자 약관 동의가 켜집니다. 저장 대상은 토큰 주인의 프로필뿐이라 요청에 대상이 없고, 요청에 실린 줄 식별자가
 * 내 것이 아니면 403으로 저장하지 않습니다.
 *
 * <p><b>저장해도 바뀌지 않는 것.</b> 경력의 증빙 확인 표시와 평점·리뷰 수는 손대지 않습니다.
 */
@Service
@Transactional(readOnly = true)
public class DeveloperProfileCommandService {
    static final String TECH_CATEGORY = "TECH_CATEGORY";

    private final DeveloperRepository developerRepository;
    private final DeveloperProfileRepository developerProfileRepository;
    private final DeveloperProfileQueryService queryService;
    private final UserService userService;
    private final SettingService settingService;
    private final CodeQueryService codeQueryService;
    private final CareerService careerService;
    private final PortfolioItemService portfolioItemService;
    private final PriceCatalogService priceCatalogService;
    private final DeveloperTechCategoryService techCategoryService;
    private final Clock clock;

    @Autowired
    public DeveloperProfileCommandService(
            DeveloperRepository developerRepository,
            DeveloperProfileRepository developerProfileRepository,
            DeveloperProfileQueryService queryService,
            UserService userService,
            SettingService settingService,
            CodeQueryService codeQueryService,
            CareerService careerService,
            PortfolioItemService portfolioItemService,
            PriceCatalogService priceCatalogService,
            DeveloperTechCategoryService techCategoryService) {
        this(developerRepository, developerProfileRepository, queryService, userService, settingService,
                codeQueryService, careerService, portfolioItemService, priceCatalogService, techCategoryService, Clock.systemUTC());
    }

    DeveloperProfileCommandService(
            DeveloperRepository developerRepository,
            DeveloperProfileRepository developerProfileRepository,
            DeveloperProfileQueryService queryService,
            UserService userService,
            SettingService settingService,
            CodeQueryService codeQueryService,
            CareerService careerService,
            PortfolioItemService portfolioItemService,
            PriceCatalogService priceCatalogService,
            DeveloperTechCategoryService techCategoryService,
            Clock clock) {
        this.developerRepository = developerRepository;
        this.developerProfileRepository = developerProfileRepository;
        this.queryService = queryService;
        this.userService = userService;
        this.settingService = settingService;
        this.codeQueryService = codeQueryService;
        this.careerService = careerService;
        this.portfolioItemService = portfolioItemService;
        this.priceCatalogService = priceCatalogService;
        this.techCategoryService = techCategoryService;
        this.clock = clock;
    }

    /**
     * 프로필을 저장합니다. 검증 순서는 코드 → 개발자 행 확보 → 프로필 → 기술 → 경력 → 작업물 → 가격표입니다.
     *
     * @throws BusinessException {@code UNAUTHORIZED} 탈퇴했거나 없는 사용자, {@code CODE_NOT_FOUND} 없는 분야·기술 코드,
     *                           {@code FORBIDDEN} 내 것이 아닌 줄 식별자, {@code FILE_NOT_ATTACHABLE} 붙일 수 없는 파일
     */
    @Transactional
    public DeveloperProfileSaveResult save(Integer userId, WriteDeveloperProfileRequest request) {
        // 프로필은 여러 목록을 전체 치환하므로 사용자 행을 잠그고 같은 사용자의 저장을 직렬화합니다.
        UserProfileView user = userService.getProfileForUpdate(userId);
        codeQueryService.requireCodes(DeveloperProfileQueryService.DEV_FIELD,
                request.fieldCodeId() == null ? List.of() : List.of(request.fieldCodeId()));
        codeQueryService.requireCodes(TECH_CATEGORY, request.techCodeIds());
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));

        Developer developer = developerRepository.findByUserIdAndDeletedFalse(userId)
                .orElseGet(() -> developerRepository.save(Developer.register(userId)));
        Optional<DeveloperProfile> found = developerProfileRepository.findByDeveloperIdAndDeletedFalse(developer.getId());
        boolean created = found.isEmpty();
        DeveloperProfile profile = found.orElseGet(() -> createProfile(userId, developer.getId(), request));
        if (!created) {
            profile.update(request.intro(), request.description(), request.yearsOfExperience(), request.fieldCodeId(),
                    request.activeFromValue(), request.activeToValue());
        }

        techCategoryService.replaceAll(developer.getId(), request.techCodeIds(), now);
        careerService.replaceAll(developer.getId(), userId, request.careers(), now);
        portfolioItemService.replaceAll(developer.getId(), userId, request.portfolioItems(), now);
        priceCatalogService.replaceAll(developer.getId(), request.prices(), now);

        return new DeveloperProfileSaveResult(created, queryService.assemble(developer, profile, user, now));
    }

    private DeveloperProfile createProfile(Integer userId, Integer developerId, WriteDeveloperProfileRequest request) {
        DeveloperProfile profile = developerProfileRepository.save(DeveloperProfile.create(developerId, request.intro(),
                request.description(), request.yearsOfExperience(), request.fieldCodeId(),
                request.activeFromValue(), request.activeToValue()));
        userService.markDevProfile(userId);
        settingService.agreeSellerTerms(userId);
        return profile;
    }
}
