package com.a501.service.domain.developer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeveloperProfileCommandServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-21T03:00:00Z"), ZoneOffset.UTC);
    private static final WriteDeveloperProfileRequest REQUEST = new WriteDeveloperProfileRequest(
            "한 줄", null, 42, null, "10:00", "19:00", List.of(21), List.of(), List.of(), List.of());

    @Mock private DeveloperRepository developerRepository;
    @Mock private DeveloperProfileRepository developerProfileRepository;
    @Mock private DeveloperProfileQueryService queryService;
    @Mock private UserService userService;
    @Mock private SettingService settingService;
    @Mock private CodeQueryService codeQueryService;
    @Mock private CareerService careerService;
    @Mock private PortfolioItemService portfolioItemService;
    @Mock private PriceCatalogService priceCatalogService;
    @Mock private DeveloperTechCategoryService techCategoryService;

    private DeveloperProfileCommandService service;

    @BeforeEach
    void setUp() {
        service = new DeveloperProfileCommandService(developerRepository, developerProfileRepository,
                queryService, userService, settingService, codeQueryService, careerService, portfolioItemService,
                priceCatalogService, techCategoryService, CLOCK);
        when(userService.getProfileForUpdate(12)).thenReturn(new UserProfileView(12, "이준", null, null));
    }

    private static Developer developer(Integer id) {
        Developer developer = Developer.register(12);
        ReflectionTestUtils.setField(developer, "id", id);
        return developer;
    }

    @Test
    void firstSaveCreatesDeveloperProfileRoleAndSellerTermsAndReports201() {
        when(developerRepository.findByUserIdAndDeletedFalse(12)).thenReturn(Optional.empty());
        when(developerRepository.save(any())).thenReturn(developer(3));
        when(developerProfileRepository.findByDeveloperIdAndDeletedFalse(3)).thenReturn(Optional.empty());
        when(developerProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DeveloperProfileSaveResult result = service.save(12, REQUEST);

        assertThat(result.created()).isTrue();
        verify(userService).markDevProfile(12);
        verify(settingService).agreeSellerTerms(12);
        verify(codeQueryService).requireCodes("DEV_FIELD", List.of(42));
        verify(codeQueryService).requireCodes("TECH_CATEGORY", List.of(21));
        verify(techCategoryService).replaceAll(eq(3), eq(List.of(21)), any());
        verify(portfolioItemService).replaceAll(eq(3), eq(12), anyList(), any());
        verify(queryService).assemble(any(), any(), any(), eq(CLOCK.instant().atOffset(ZoneOffset.UTC).toLocalDateTime()));
    }

    @Test
    void laterSaveUpdatesTheExistingProfileAndReports200() {
        Developer developer = developer(3);
        DeveloperProfile profile = DeveloperProfile.create(3, "옛 소개", null, null, null, null, null);
        when(developerRepository.findByUserIdAndDeletedFalse(12)).thenReturn(Optional.of(developer));
        when(developerProfileRepository.findByDeveloperIdAndDeletedFalse(3)).thenReturn(Optional.of(profile));

        DeveloperProfileSaveResult result = service.save(12, REQUEST);

        assertThat(result.created()).isFalse();
        assertThat(profile.getIntro()).isEqualTo("한 줄");
        assertThat(profile.getFieldCodeId()).isEqualTo(42);
        verify(userService, never()).markDevProfile(any());
        verify(settingService, never()).agreeSellerTerms(any());
        verify(developerRepository, never()).save(any());
    }

    @Test
    void everySaveLocksTheUserBeforeReplacingTheProfile() {
        Developer developer = developer(5);
        when(developerRepository.findByUserIdAndDeletedFalse(12)).thenReturn(Optional.of(developer));
        when(developerProfileRepository.findByDeveloperIdAndDeletedFalse(5))
                .thenReturn(Optional.of(DeveloperProfile.create(5, "예전", null, null, null, null, null)));

        service.save(12, REQUEST);

        verify(userService).getProfileForUpdate(12);
        verify(priceCatalogService).replaceAll(eq(5), anyList(), any());
    }
}
