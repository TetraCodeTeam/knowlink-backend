package com.knowlink.api.notifications.repositories;

import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.data.models.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipient_UserId(UUID userId, Pageable pageable);

    Page<Notification> findByRecipient_UserIdAndReadAtIsNull(UUID userId, Pageable pageable);

    Page<Notification> findByRecipient_UserIdAndReadAtIsNotNull(UUID userId, Pageable pageable);

    List<Notification> findByRecipient_UserIdOrderByCreatedAtDesc(UUID userId);

    List<Notification> findByRecipient_UserIdAndReadAtIsNull(UUID userId);

    List<Notification> findByRecipient_UserIdAndTypeInAndReadAtIsNull(UUID userId, List<NotificationType> types);

    long countByRecipient_UserIdAndReadAtIsNull(UUID userId);

    Optional<Notification> findByNotificationIdAndRecipient_UserId(UUID notificationId, UUID userId);

    boolean existsByRecipient_UserIdAndDedupeKey(UUID userId, String dedupeKey);
}