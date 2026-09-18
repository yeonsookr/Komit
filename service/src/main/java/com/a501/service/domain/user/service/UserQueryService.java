package com.a501.service.domain.user.service;

import com.a501.service.domain.developer.service.DeveloperLookupService;
import com.a501.service.domain.notification.service.NotificationQueryService;
import com.a501.service.domain.user.dto.UserSnapshot;
import com.a501.service.domain.user.dto.response.MeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {
    private final UserService userService;
    private final DeveloperLookupService developerLookupService;
    private final NotificationQueryService notificationQueryService;

    public MeResponse getMe(Integer userId) {
        UserSnapshot user = userService.getActiveUser(userId);
        Integer developerId = developerLookupService.findDeveloperId(userId);
        int unreadCount = notificationQueryService.countUnread(userId);
        return new MeResponse(
                user.userId(),
                user.name(),
                user.profileImageFileId(),
                user.hasDevProfile(),
                developerId,
                unreadCount);
    }
}
