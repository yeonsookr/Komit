package com.a501.service.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.security.SecretStringCrypto;
import com.a501.service.domain.setting.service.SettingService;
import com.a501.service.domain.user.dto.KakaoUserData;
import com.a501.service.domain.user.dto.UserLoginResult;
import com.a501.service.domain.user.entity.User;
import com.a501.service.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");
    private static final LocalDateTime NOW_UTC = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;
    @Mock
    private SettingService settingService;
    @Mock
    private SecretStringCrypto stringCrypto;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, settingService, stringCrypto, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("처음 들어온 카카오 계정은 미동의 설정으로 가입한다")
    void registersNewUserWithPendingTerms() {
        when(stringCrypto.encrypt("kakao-refresh")).thenReturn("protected");
        when(userRepository.findByKakaoId(123L)).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 12);
            return saved;
        });

        UserLoginResult result = userService.loginOrRegister(kakaoUserData());

        assertThat(result.newUser()).isTrue();
        assertThat(result.withdrawn()).isFalse();
        assertThat(result.user().userId()).isEqualTo(12);
        assertThat(result.user().name()).isEqualTo("김미미");
        assertThat(result.user().hasDevProfile()).isFalse();
        verify(settingService).createForNewUser(12);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        verify(userRepository).markSignupPending(12);
        assertThat(ReflectionTestUtils.getField(captor.getValue(), "kakaoRefreshToken")).isEqualTo("protected");
        assertThat(ReflectionTestUtils.getField(captor.getValue(), "lastLoginAt")).isEqualTo(NOW_UTC);
    }

    @Test
    @DisplayName("기존 사용자는 카카오 정보를 갱신하고 새 가입으로 보지 않는다")
    void existingUserLogsInAndRefreshesKakaoData() {
        User existing = existingUser(12);
        when(stringCrypto.encrypt("kakao-refresh")).thenReturn("protected-again");
        when(userRepository.findByKakaoId(123L)).thenReturn(Optional.of(existing));

        UserLoginResult result = userService.loginOrRegister(kakaoUserData());

        assertThat(result.newUser()).isFalse();
        assertThat(result.user().userId()).isEqualTo(12);
        assertThat(ReflectionTestUtils.getField(existing, "kakaoRefreshToken")).isEqualTo("protected-again");
        assertThat(ReflectionTestUtils.getField(existing, "lastLoginAt")).isEqualTo(NOW_UTC);
        verify(userRepository, never()).save(any());
        verify(settingService, never()).createForNewUser(any());
    }

    @Test
    @DisplayName("가입 진행 중 다시 로그인해도 가입 흐름으로 이어간다")
    void pendingUserResumesSignup() {
        User pending = existingUser(12);
        ReflectionTestUtils.setField(pending, "recordStatus", "PENDING");
        when(stringCrypto.encrypt("kakao-refresh")).thenReturn("protected-again");
        when(userRepository.findByKakaoId(123L)).thenReturn(Optional.of(pending));

        assertThat(userService.loginOrRegister(kakaoUserData()).newUser()).isTrue();
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("R-AUTH-05: 탈퇴한 카카오 계정은 다시 가입하지도 로그인하지도 못한다")
    void withdrawnUserIsRejectedWithoutChanges() {
        User withdrawn = existingUser(12);
        ReflectionTestUtils.setField(
                withdrawn,
                "deletedAt",
                LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC));
        when(stringCrypto.encrypt("kakao-refresh")).thenReturn("protected");
        when(userRepository.findByKakaoId(123L)).thenReturn(Optional.of(withdrawn));

        UserLoginResult result = userService.loginOrRegister(kakaoUserData());

        assertThat(result.withdrawn()).isTrue();
        assertThat(result.user()).isNull();
        assertThat(ReflectionTestUtils.getField(withdrawn, "lastLoginAt")).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("카카오 연결을 끊은 사용자도 탈퇴로 본다")
    void unlinkedUserIsNotActive() {
        User unlinked = existingUser(12);
        ReflectionTestUtils.setField(unlinked, "unlinkedAt", NOW_UTC);
        when(userRepository.findById(12)).thenReturn(Optional.of(unlinked));

        assertThat(userService.isActive(12)).isFalse();
        assertThatThrownBy(() -> userService.getActiveUser(12))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("없는 사용자는 활성 사용자가 아니다")
    void missingUserIsNotActive() {
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        assertThat(userService.isActive(99)).isFalse();
    }

    @Test
    @DisplayName("올린 사진을 프로필 사진으로 걸고 카카오 사진 주소는 그대로 둔다")
    void changeProfileImageKeepsKakaoUrl() {
        User user = existingUser(12);
        ReflectionTestUtils.setField(user, "kakaoProfileImageUrl", "https://k.kakaocdn.net/profile.jpg");
        when(userRepository.findById(12)).thenReturn(Optional.of(user));

        userService.changeProfileImage(12, 512);

        assertThat(user.getProfileImageFileId()).isEqualTo(512);
        assertThat(user.getKakaoProfileImageUrl()).isEqualTo("https://k.kakaocdn.net/profile.jpg");
    }

    @Test
    @DisplayName("탈퇴한 사용자는 프로필 사진을 바꾸지 못하고 401이다")
    void withdrawnUserCannotChangeProfileImage() {
        User withdrawn = existingUser(12);
        ReflectionTestUtils.setField(withdrawn, "unlinkedAt", NOW_UTC);
        when(userRepository.findById(12)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> userService.changeProfileImage(12, 512))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
        assertThat(withdrawn.getProfileImageFileId()).isNull();
    }

    private KakaoUserData kakaoUserData() {
        return new KakaoUserData(
                123L,
                "김미미",
                "https://k.kakaocdn.net/profile.jpg",
                null,
                false,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                "kakao-refresh",
                NOW.plusSeconds(5_184_000L));
    }

    private User existingUser(Integer id) {
        User user = User.register(
                123L, "김미미", null, null, false, null,
                Instant.parse("2026-01-01T00:00:00Z"), "old-protected",
                NOW.plusSeconds(1_000L), NOW.minusSeconds(86_400L));
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "lastLoginAt", null);
        return user;
    }
}
