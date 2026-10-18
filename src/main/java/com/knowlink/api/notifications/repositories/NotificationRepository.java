package com.knowlink.api.notifications.repositories;

import com.knowlink.api.notifications.data.models.Notification;
import com.knowlink.api.notifications.data.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipient_UserIdOrderByCreatedAtDesc(UUID userId);

    List<Notification> findByRecipient_UserIdAndReadAtIsNull(UUID userId);

    List<Notification> findByRecipient_UserIdAndTypeInAndReadAtIsNull(UUID userId, List<NotificationType> types);

    Optional<Notification> findByNotificationIdAndRecipient_UserId(UUID notificationId, UUID userId);
}
