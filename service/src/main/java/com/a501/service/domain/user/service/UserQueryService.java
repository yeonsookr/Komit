package com.a501.service.domain.user.service;

import com.a501.service.domain.user.entity.User;
import com.a501.service.domain.user.repository.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.a501.service.domain.developer.service.DeveloperLookupService;
import com.a501.service.domain.file.service.FileLookupService;
import com.a501.service.domain.notification.service.NotificationQueryService;
import com.a501.service.domain.user.dto.UserProfileView;
import com.a501.service.domain.user.dto.UserSnapshot;
import com.a501.service.domain.user.dto.response.MeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {
    private final UserRepository userRepository;
    private final UserService userService;
    private final DeveloperLookupService developerLookupService;
    private final NotificationQueryService notificationQueryService;
    private final FileLookupService fileLookupService;

    /** 탈퇴한 사람의 이름 자리에 들어갑니다. (R-ACC 탈퇴 시 식별값 제거) */
    private static final String WITHDRAWN_NAME = "탈퇴한 사용자";

    /**
     * 화면에 보이는 이름.
     *
     * <p>탈퇴한 사람도 이름 자리를 채웁니다. 방 머리처럼 지난 상대를 가리키는 자리가 있어서,
     * 여기서 막으면 그 방이 통째로 열리지 않습니다. 다만 이름 대신 {@code 탈퇴한 사용자} 가
     * 나갑니다. 없는 사용자만 비어 있습니다.
     *
     * @param userId 사용자 id
     */
    public Optional<String> findName(Integer userId) {
        return userRepository.findById(userId).map(UserQueryService::displayName);
    }

    /**
     * 사용자 식별자별 표시 이름. 없는 사용자는 결과에서 빠집니다.
     *
     * @param userIds 사용자 id 목록
     * @return 사용자 id → 표시 이름
     */
    public Map<Integer, String> findNames(Collection<Integer> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, UserQueryService::displayName));
    }

    private static String displayName(User user) {
        return user.isDeleted() ? WITHDRAWN_NAME : user.getName();
    }

    public MeResponse getMe(Integer userId) {
        UserSnapshot user = userService.getActiveUser(userId);
        Integer developerId = developerLookupService.findDeveloperId(userId);
        int unreadCount = notificationQueryService.countUnread(userId);
        return new MeResponse(
                user.userId(),
                user.name(),
                user.profileImageFileId(),
                findProfileImageUrl(userId),
                user.hasDevProfile(),
                developerId,
                unreadCount);
    }

    /**
     * 화면에 그대로 거는 내 프로필 사진 주소. 직접 올린 사진, 카카오 사진 순서로 고르고 둘 다 없으면 null입니다.
     * 올린 사진의 주소는 임시 주소라 저장하지 않고 부를 때마다 새로 만듭니다. (R-FILE-03)
     * 올린 파일을 더는 읽을 수 없으면 카카오 사진으로 물러납니다.
     *
     * @throws com.a501.service.common.exception.BusinessException {@code UNAUTHORIZED} 탈퇴했거나 없는 사용자일 때
     */
    public String findProfileImageUrl(Integer userId) {
        UserProfileView profile = userService.getProfile(userId);
        if (profile.profileImageFileId() == null) {
            return profile.kakaoProfileImageUrl();
        }
        return fileLookupService.findReadUrls(List.of(profile.profileImageFileId()))
                .getOrDefault(profile.profileImageFileId(), profile.kakaoProfileImageUrl());
    }
}
