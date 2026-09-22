package com.a501.service.domain.user.service;

import java.time.Clock;
import java.time.Instant;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.security.SecretStringCrypto;
import com.a501.service.domain.setting.service.SettingService;
import com.a501.service.domain.user.dto.KakaoUserData;
import com.a501.service.domain.user.dto.MyPageUserView;
import com.a501.service.domain.user.dto.UserLoginResult;
import com.a501.service.domain.user.dto.UserSnapshot;
import com.a501.service.domain.user.dto.UserProfileView;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.a501.service.domain.user.entity.User;
import com.a501.service.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {
    /** 탈퇴한 사용자의 표시 이름. 목 명세 {@code ReviewResponse.clientName}과 같습니다. */
    static final String WITHDRAWN_NAME = "탈퇴한 사용자";

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
     * 카카오 계정으로 로그인하거나, 처음 들어온 사용자면 가입 진행 상태로 저장합니다.
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

    /** 프로필 화면이 쓰는 계정 표시 정보. 탈퇴했거나 없는 사용자는 인증 실패로 봅니다. */
    public UserProfileView getProfile(Integer userId) {
        User user = userRepository.findById(userId)
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        return profile(user);
    }

    /**
     * 사용자 식별자별 공개 표시 정보. 개발자 목록·상세처럼 남의 이름과 사진을 보여줄 때 씁니다.
     * 탈퇴한 사용자는 결과에 없습니다. 탈퇴 회원의 이름·프로필을 노출하지 않기 위해서입니다. (R-ACC-07)
     */
    public Map<Integer, UserProfileView> findProfiles(Collection<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .filter(user -> !user.isWithdrawn())
                .collect(Collectors.toMap(User::getId, UserService::profile));
    }

    /**
     * 표시 이름에 검색어가 들어 있는 살아 있는 사용자의 식별자. 개발자 찾기의 이름 검색이 개발자 조건으로 바꿔 쓰기 위해 부릅니다.
     * 검색어는 호출하는 쪽이 다듬어 넘기며, 대소문자를 가립니다(목과 같음).
     */
    public List<Integer> findActiveUserIdsByName(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return List.of();
        }
        return userRepository.findByNameContainingAndDeletedFalse(keyword).stream()
                .filter(user -> !user.isWithdrawn())
                .map(User::getId)
                .toList();
    }

    /** 마이페이지 계정 칸에 필요한 값. 탈퇴했거나 없는 사용자는 인증 실패로 봅니다. */
    public MyPageUserView getMyPageUser(Integer userId) {
        User user = userRepository.findById(userId)
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        return new MyPageUserView(
                user.getName(),
                user.getEmail(),
                user.getProfileImageFileId(),
                user.getKakaoProfileImageUrl());
    }

    /**
     * 개발자 프로필을 통째로 저장하는 동안 같은 사용자의 다른 저장을 기다리게 합니다.
     * 개발자·프로필·하위 목록을 하나의 요청 결과로 남기기 위한 DB 잠금입니다. (BACKEND_CONVENTIONS 16)
     */
    @Transactional
    public UserProfileView getProfileForUpdate(Integer userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        return profile(user);
    }

    /** 개발자 프로필을 처음 만들 때 개발자 역할을 더합니다. (R-ACC-08) */
    @Transactional
    public void markDevProfile(Integer userId) {
        User user = userRepository.findById(userId)
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        user.markDevProfile();
    }

    /**
     * 사용자 식별자별로 가린 표시 이름을 돌려줍니다. 리뷰 작성자처럼 남에게 보이는 이름에 씁니다.
     * 첫 글자와 끝 글자만 남기고 가운데를 {@code O}로 바꾸며, 두 글자면 끝 글자를 바꿉니다. 탈퇴한 사용자는 {@code 탈퇴한 사용자}입니다. (R-ACC-07)
     */
    public Map<Integer, String> findMaskedNames(Collection<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user.isWithdrawn() ? WITHDRAWN_NAME : mask(user.getName())));
    }

    static String mask(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "O";
        }
        return name.charAt(0) + "O".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    public boolean isActive(Integer userId) {
        return userRepository.findById(userId)
                .map(user -> !user.isWithdrawn())
                .orElse(false);
    }

    public boolean isSignupComplete(Integer userId) {
        return userRepository.findById(userId)
                .map(user -> !user.isWithdrawn() && "ACTIVE".equals(user.getRecordStatus()))
                .orElse(false);
    }

    @Transactional
    public void completeSignup(Integer userId) {
        if (userRepository.completeSignup(userId) == 0 && !isSignupComplete(userId)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
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
        return UserLoginResult.accepted(snapshot(user), "PENDING".equals(user.getRecordStatus()));
    }

    private UserLoginResult register(KakaoUserData data, String protectedRefreshToken, Instant now) {
        User saved = userRepository.saveAndFlush(User.register(
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
        Integer userId = saved.getId();
        UserSnapshot newUser = snapshot(saved);
        userRepository.markSignupPending(userId);
        settingService.createForNewUser(userId);
        return UserLoginResult.accepted(newUser, true);
    }

    private static UserProfileView profile(User user) {
        return new UserProfileView(user.getId(), user.getName(), user.getProfileImageFileId(), user.getKakaoProfileImageUrl());
    }

    private UserSnapshot snapshot(User user) {
        return new UserSnapshot(
                user.getId(),
                user.getName(),
                user.getProfileImageFileId(),
                user.hasDevProfile());
    }
}
