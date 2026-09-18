package com.a501.service.domain.user.service;

import java.time.Clock;
import java.time.Instant;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.security.SecretStringCrypto;
import com.a501.service.domain.setting.service.SettingService;
import com.a501.service.domain.user.dto.KakaoUserData;
import com.a501.service.domain.user.dto.UserLoginResult;
import com.a501.service.domain.user.dto.UserSnapshot;
import com.a501.service.domain.user.entity.User;
import com.a501.service.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final SettingService settingService;
    private final SecretStringCrypto stringCrypto;
    private final Clock clock;

    @Autowired
    public UserService(
            UserRepository userRepository,
            SettingService settingService,
            SecretStringCrypto stringCrypto) {
        this(userRepository, settingService, stringCrypto, Clock.systemUTC());
    }

    UserService(
            UserRepository userRepository,
            SettingService settingService,
            SecretStringCrypto stringCrypto,
            Clock clock) {
        this.userRepository = userRepository;
        this.settingService = settingService;
        this.stringCrypto = stringCrypto;
        this.clock = clock;
    }

    /**
     * 카카오 계정으로 로그인하거나, 처음 들어온 사용자면 가입까지 끝냅니다.
     * 탈퇴한 사용자는 같은 카카오 계정으로 다시 가입할 수 없습니다. (R-AUTH-05)
     */
    @Transactional
    public UserLoginResult loginOrRegister(KakaoUserData data) {
        Instant now = clock.instant();
        String protectedRefreshToken = stringCrypto.encrypt(data.kakaoRefreshToken());

        return userRepository.findByKakaoId(data.kakaoId())
                .map(user -> loginExisting(user, data, protectedRefreshToken, now))
                .orElseGet(() -> register(data, protectedRefreshToken, now));
    }

    public UserSnapshot getActiveUser(Integer userId) {
        User user = userRepository.findById(userId)
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        return snapshot(user);
    }

    public boolean isActive(Integer userId) {
        return userRepository.findById(userId)
                .map(user -> !user.isWithdrawn())
                .orElse(false);
    }

    private UserLoginResult loginExisting(
            User user,
            KakaoUserData data,
            String protectedRefreshToken,
            Instant now) {
        if (user.isWithdrawn()) {
            return UserLoginResult.rejectedWithdrawnUser();
        }
        user.recordKakaoLogin(
                data.profileImageUrl(),
                data.email(),
                data.emailVerified(),
                data.emailValid(),
                data.connectedAt(),
                protectedRefreshToken,
                data.kakaoRefreshTokenExpiresAt(),
                now);
        return UserLoginResult.accepted(snapshot(user), false);
    }

    private UserLoginResult register(KakaoUserData data, String protectedRefreshToken, Instant now) {
        User saved = userRepository.save(User.register(
                data.kakaoId(),
                data.nickname(),
                data.profileImageUrl(),
                data.email(),
                data.emailVerified(),
                data.emailValid(),
                data.connectedAt(),
                protectedRefreshToken,
                data.kakaoRefreshTokenExpiresAt(),
                now));
        settingService.createWithRequiredTerms(saved.getId());
        return UserLoginResult.accepted(snapshot(saved), true);
    }

    private UserSnapshot snapshot(User user) {
        return new UserSnapshot(
                user.getId(),
                user.getName(),
                user.getProfileImageFileId(),
                user.hasDevProfile());
    }
}
