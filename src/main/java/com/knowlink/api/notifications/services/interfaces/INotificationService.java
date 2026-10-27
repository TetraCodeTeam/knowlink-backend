package com.knowlink.api.notifications.services.interfaces;

import com.knowlink.api.notifications.controllers.responses.NotificationResponse;
import com.knowlink.api.notifications.controllers.responses.UnreadCountResponse;
import com.knowlink.api.notifications.data.enums.NotificationReadFilter;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.responses.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface INotificationService {

    void createSupportNotification(
            UUID recipientUserId,
            Role recipientRole,
            NotificationType type,
            UUID resourceId,
            String title,
            String message);

    /**
     * Creates a notification for a single user. Idempotent on (recipient, dedupeKey).
     */
    void notifyUser(UUID recipientUserId, Role recipientRole, NotificationType type, String title,
                    String message, UUID referenceId, String dedupeKey);

    /**
     * Creates a notification that expires at the given instant. Idempotent on (recipient, dedupeKey).
     */
    void notifyUser(UUID recipientUserId, Role recipientRole, NotificationType type, String title,
                    String message, UUID referenceId, String dedupeKey, LocalDateTime expiresAt);

    /**
     * Creates one notification per active admin account. Idempotent on (recipient, dedupeKey).
     */
    void notifyAdmins(NotificationType type, String title, String message, UUID referenceId, String dedupeKey);

    PagedResponse<NotificationResponse> getNotifications(UUID userId, Role activeRole,
            NotificationReadFilter readFilter, Pageable pageable);

    UnreadCountResponse getUnreadCount(UUID userId, Role activeRole);

    long countUnreadSupportForUser(UUID userId, Role activeRole);

    void markAsRead(UUID userId, UUID notificationId);

    void markAllAsRead(UUID userId, Role activeRole);

    void markSupportAsRead(UUID userId, Role activeRole);

    /**
     * Marks the notification (recipient, type, resourceId) as read and resolved.
     * Idempotent; does nothing when the notification does not exist.
     */
    void resolveNotification(UUID recipientUserId, NotificationType type, UUID resourceId);
}