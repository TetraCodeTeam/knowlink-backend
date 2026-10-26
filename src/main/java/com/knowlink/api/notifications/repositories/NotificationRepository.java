package com.knowlink.api.notifications.repositories;

import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.data.models.Notification;
import com.knowlink.api.security.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipient_UserIdAndRecipientRole(
            UUID userId, Role recipientRole, Pageable pageable);

    Page<Notification> findByRecipient_UserIdAndRecipientRoleAndReadAtIsNotNull(
            UUID userId, Role recipientRole, Pageable pageable);

    List<Notification> findByRecipient_UserIdAndRecipientRoleAndReadAtIsNull(
            UUID userId, Role recipientRole);

    List<Notification> findByRecipient_UserIdAndRecipientRoleAndTypeInAndReadAtIsNull(
            UUID userId, Role recipientRole, List<NotificationType> types);

    @Query(value = """
            SELECT n FROM Notification n
            WHERE n.recipient.userId = :userId
              AND n.recipientRole = :recipientRole
              AND n.readAt IS NULL
              AND (n.expiresAt IS NULL OR n.expiresAt >= :now)
            """,
            countQuery = """
            SELECT COUNT(n) FROM Notification n
            WHERE n.recipient.userId = :userId
              AND n.recipientRole = :recipientRole
              AND n.readAt IS NULL
              AND (n.expiresAt IS NULL OR n.expiresAt >= :now)
            """)
    Page<Notification> findUnreadVisibleByRecipient(
            @Param("userId") UUID userId,
            @Param("recipientRole") Role recipientRole,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.recipient.userId = :userId
              AND n.recipientRole = :recipientRole
              AND n.readAt IS NULL
              AND (n.expiresAt IS NULL OR n.expiresAt >= :now)
            """)
    long countUnreadVisibleByRecipient(
            @Param("userId") UUID userId,
            @Param("recipientRole") Role recipientRole,
            @Param("now") LocalDateTime now);

    Optional<Notification> findByNotificationIdAndRecipient_UserId(UUID notificationId, UUID userId);

    Optional<Notification> findByRecipient_UserIdAndTypeAndResourceId(
            UUID userId, NotificationType type, UUID resourceId);

    boolean existsByRecipient_UserIdAndDedupeKey(UUID userId, String dedupeKey);
}