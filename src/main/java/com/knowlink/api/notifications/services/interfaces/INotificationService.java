package com.knowlink.api.notifications.services.interfaces;

import com.knowlink.api.notifications.controllers.responses.NotificationResponse;
import com.knowlink.api.notifications.controllers.responses.UnreadCountResponse;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.shared.responses.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface INotificationService {

    void createSupportNotification(
            UUID recipientUserId,
            NotificationType type,
            UUID resourceId,
            String title,
            String message);

    /**
     * Creates a notification for a single user. Idempotent on (recipient, dedupeKey).
     */
    void notifyUser(UUID recipientUserId, NotificationType type, String message,
                    UUID referenceId, String dedupeKey);

    /**
     * Creates one notification per active admin account. Idempotent on (recipient, dedupeKey).
     */
    void notifyAdmins(NotificationType type, String message, UUID referenceId, String dedupeKey);

    PagedResponse<NotificationResponse> getNotifications(UUID userId, boolean unreadOnly, Pageable pageable);

    UnreadCountResponse getUnreadCount(UUID userId);

    long countUnreadSupportForUser(UUID userId);

    void markAsRead(UUID userId, UUID notificationId);

    void markAllAsRead(UUID userId);

    void markSupportAsRead(UUID userId);
}