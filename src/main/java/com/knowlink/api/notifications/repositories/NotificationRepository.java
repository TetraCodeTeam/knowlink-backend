package com.knowlink.api.notifications.repositories;

import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.data.models.Notification;
import com.knowlink.api.security.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipient_UserIdAndRecipientRole(
            UUID userId, Role recipientRole, Pageable pageable);

    Page<Notification> findByRecipient_UserIdAndRecipientRoleAndReadAtIsNull(
            UUID userId, Role recipientRole, Pageable pageable);

    Page<Notification> findByRecipient_UserIdAndRecipientRoleAndReadAtIsNotNull(
            UUID userId, Role recipientRole, Pageable pageable);

    List<Notification> findByRecipient_UserIdAndRecipientRoleAndReadAtIsNull(
            UUID userId, Role recipientRole);

    List<Notification> findByRecipient_UserIdAndRecipientRoleAndTypeInAndReadAtIsNull(
            UUID userId, Role recipientRole, List<NotificationType> types);

    long countByRecipient_UserIdAndRecipientRoleAndReadAtIsNull(UUID userId, Role recipientRole);

    long countByRecipient_UserIdAndRecipientRole(UUID userId, Role recipientRole);

    long countByResourceIdAndType(UUID resourceId, NotificationType type);

    Optional<Notification> findByNotificationIdAndRecipient_UserId(UUID notificationId, UUID userId);

    boolean existsByRecipient_UserIdAndDedupeKey(UUID userId, String dedupeKey);
}