package com.a501.service.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.domain.developer.service.DeveloperLookupService;
import com.a501.service.domain.file.service.FileLookupService;
import com.a501.service.domain.notification.service.NotificationQueryService;
import com.a501.service.domain.user.dto.UserProfileView;
import com.a501.service.domain.user.dto.UserSnapshot;
import com.a501.service.domain.user.dto.response.MeResponse;
import com.a501.service.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 목 명세 {@code MeResponse.profileImageUrl}: 직접 올린 사진, 카카오 사진 순서로 고르고 둘 다 없으면 null. */
@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {
    private static final String KAKAO_URL = "https://k.kakaocdn.net/dn/profile/mimi.jpg";
    private static final String UPLOADED_URL = "https://storage.komit.site/profile-image/512?sig=abc";

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private DeveloperLookupService developerLookupService;
    @Mock
    private NotificationQueryService notificationQueryService;
    @Mock
    private FileLookupService fileLookupService;
    @InjectMocks
    private UserQueryService userQueryService;

    @Test
    @DisplayName("직접 올린 사진이 있으면 그 사진의 임시 주소를 쓴다")
    void uploadedImageWins() {
        when(userService.getProfile(12)).thenReturn(new UserProfileView(12, "김미미", 512, KAKAO_URL));
        when(fileLookupService.findReadUrls(List.of(512))).thenReturn(Map.of(512, UPLOADED_URL));

        assertThat(userQueryService.findProfileImageUrl(12)).isEqualTo(UPLOADED_URL);
    }

    @Test
    @DisplayName("올린 사진이 없으면 저장소를 부르지 않고 카카오 사진을 쓴다")
    void kakaoImageWhenNothingUploaded() {
        when(userService.getProfile(12)).thenReturn(new UserProfileView(12, "김미미", null, KAKAO_URL));

        assertThat(userQueryService.findProfileImageUrl(12)).isEqualTo(KAKAO_URL);
        verify(fileLookupService, never()).findReadUrls(any());
    }

    @Test
    @DisplayName("올린 파일을 더는 읽을 수 없으면 카카오 사진으로 물러난다")
    void kakaoImageWhenUploadedFileIsGone() {
        when(userService.getProfile(12)).thenReturn(new UserProfileView(12, "김미미", 512, KAKAO_URL));
        when(fileLookupService.findReadUrls(List.of(512))).thenReturn(Map.of());

        assertThat(userQueryService.findProfileImageUrl(12)).isEqualTo(KAKAO_URL);
    }

    @Test
    @DisplayName("둘 다 없으면 null이다")
    void nullWhenNoImageAtAll() {
        when(userService.getProfile(12)).thenReturn(new UserProfileView(12, "김미미", null, null));

        assertThat(userQueryService.findProfileImageUrl(12)).isNull();
    }

    @Test
    @DisplayName("내 정보에 사진 파일 식별자와 화면에 걸 주소가 함께 담긴다")
    void meCarriesProfileImageUrl() {
        when(userService.getActiveUser(12)).thenReturn(new UserSnapshot(12, "김미미", 512, true));
        when(userService.getProfile(12)).thenReturn(new UserProfileView(12, "김미미", 512, KAKAO_URL));
        when(fileLookupService.findReadUrls(List.of(512))).thenReturn(Map.of(512, UPLOADED_URL));
        when(developerLookupService.findDeveloperId(12)).thenReturn(9);
        when(notificationQueryService.countUnread(12)).thenReturn(3);

        assertThat(userQueryService.getMe(12))
                .isEqualTo(new MeResponse(12, "김미미", 512, UPLOADED_URL, true, 9, 3));
    }
}
