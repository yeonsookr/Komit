package com.a501.service.domain.notification.repository;

import com.a501.service.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    long countByUserIdAndReadFalseAndDeletedFalse(Integer userId);
}
