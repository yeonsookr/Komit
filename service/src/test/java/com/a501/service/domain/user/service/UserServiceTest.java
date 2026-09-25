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

    @Test
    @DisplayName("이름을 바꾸면 표시 이름만 바뀌고, 다음 카카오 로그인이 닉네임으로 되돌리지 않는다")
    void renameSurvivesNextKakaoLogin() {
        User user = existingUser(12);
        when(userRepository.findById(12)).thenReturn(Optional.of(user));
        when(stringCrypto.encrypt("kakao-refresh")).thenReturn("protected");
        when(userRepository.findByKakaoId(123L)).thenReturn(Optional.of(user));

        userService.rename(12, "새이름");
        userService.loginOrRegister(kakaoUserData());

        assertThat(user.getName()).isEqualTo("새이름");
    }

    @Test
    @DisplayName("탈퇴한 사용자는 이름을 바꾸지 못하고 401이다")
    void withdrawnUserCannotRename() {
        User withdrawn = existingUser(12);
        ReflectionTestUtils.setField(withdrawn, "unlinkedAt", NOW_UTC);
        when(userRepository.findById(12)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> userService.rename(12, "새이름"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
        assertThat(withdrawn.getName()).isEqualTo("김미미");
    }

    @Test
    @DisplayName("탈퇴하면 행을 지우지 않고 삭제 표시와 시각만 남기며, 그 뒤로는 탈퇴한 사용자다")
    void withdrawMarksDeletedWithoutRemovingRow() {
        User user = existingUser(12);
        when(userRepository.findByIdForUpdate(12)).thenReturn(Optional.of(user));

        LocalDateTime deletedAt = userService.withdraw(12);

        assertThat(deletedAt).isEqualTo(NOW_UTC);
        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getRecordStatus()).isEqualTo("DELETED");
        assertThat(user.isWithdrawn()).isTrue();
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("이미 탈퇴한 사용자가 다시 탈퇴하면 401이고 삭제 시각은 처음 값 그대로다")
    void withdrawingTwiceIsUnauthorized() {
        User withdrawn = existingUser(12);
        LocalDateTime first = NOW_UTC.minusDays(1);
        ReflectionTestUtils.setField(withdrawn, "deletedAt", first);
        when(userRepository.findByIdForUpdate(12)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> userService.withdraw(12))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
        assertThat(withdrawn.getDeletedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("R-ACC-06: 탈퇴 계정의 식별 정보를 지우고 카카오 회원번호를 음수로 바꿔 새 가입 자리를 비운다")
    void anonymizeClearsIdentityAndFreesKakaoId() {
        User user = existingUser(12);
        ReflectionTestUtils.setField(user, "email", "mimi@kakao.com");
        ReflectionTestUtils.setField(user, "kakaoProfileImageUrl", "https://k.kakaocdn.net/profile.jpg");
        ReflectionTestUtils.setField(user, "profileImageFileId", 512);
        user.withdraw(NOW_UTC.minusDays(31));
        when(userRepository.findByIdForUpdate(12)).thenReturn(Optional.of(user));

        assertThat(userService.anonymize(12)).isTrue();

        assertThat(user.getKakaoId()).isEqualTo(-12L);
        assertThat(user.isAnonymized()).isTrue();
        assertThat(user.getName()).isEqualTo("탈퇴한 사용자");
        assertThat(user.getEmail()).isNull();
        assertThat(user.getKakaoProfileImageUrl()).isNull();
        assertThat(user.getProfileImageFileId()).isNull();
        assertThat(user.getKakaoConnectedAt()).isNull();
        assertThat(ReflectionTestUtils.getField(user, "kakaoRefreshToken")).isNull();
        assertThat(user.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("이미 지웠거나 탈퇴하지 않은 계정은 다시 바꾸지 않는다")
    void anonymizeIsIdempotentAndOnlyForWithdrawn() {
        User active = existingUser(12);
        when(userRepository.findByIdForUpdate(12)).thenReturn(Optional.of(active));
        assertThat(userService.anonymize(12)).isFalse();
        assertThat(active.getKakaoId()).isEqualTo(123L);

        User withdrawn = existingUser(13);
        withdrawn.withdraw(NOW_UTC.minusDays(31));
        when(userRepository.findByIdForUpdate(13)).thenReturn(Optional.of(withdrawn));
        assertThat(userService.anonymize(13)).isTrue();
        assertThat(userService.anonymize(13)).isFalse();
        assertThat(withdrawn.getKakaoId()).isEqualTo(-13L);
    }

    @Test
    @DisplayName("R-AUTH-05: 기준 시각에 딱 맞춰 탈퇴한 계정은 찾고, 그보다 늦게 탈퇴한 계정은 찾지 않는다")
    void findWithdrawnBeforeUsesCutoffInclusive() {
        LocalDateTime cutoff = NOW_UTC.minusDays(30);
        User onTheLine = existingUser(12);
        onTheLine.withdraw(cutoff);
        when(userRepository.findByKakaoId(123L)).thenReturn(Optional.of(onTheLine));
        assertThat(userService.findWithdrawnBefore(123L, cutoff)).contains(12);

        User recent = existingUser(12);
        recent.withdraw(cutoff.plusSeconds(1));
        when(userRepository.findByKakaoId(123L)).thenReturn(Optional.of(recent));
        assertThat(userService.findWithdrawnBefore(123L, cutoff)).isEmpty();
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
