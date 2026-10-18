package com.knowlink.api.notifications.services.interfaces;

import com.knowlink.api.notifications.controllers.responses.NotificationResponse;
import com.knowlink.api.notifications.data.enums.NotificationType;

import java.util.List;
import java.util.UUID;

public interface INotificationService {

    void createSupportNotification(
            UUID recipientUserId,
            NotificationType type,
            UUID resourceId,
            String title,
            String message);

    List<NotificationResponse> listForUser(UUID userId);

    long countUnreadForUser(UUID userId);

    long countUnreadSupportForUser(UUID userId);

    void markAsRead(UUID userId, UUID notificationId);

    void markAllAsRead(UUID userId);

    void markSupportAsRead(UUID userId);
}
