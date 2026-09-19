package com.a501.service.domain.notification.service;

import com.a501.service.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {
    private final NotificationRepository notificationRepository;

    public int countUnread(Integer userId) {
        return Math.toIntExact(notificationRepository.countByUserIdAndReadFalseAndDeletedFalse(userId));
    }
}
