package com.knowlink.api.notifications.data.mappers;

import com.knowlink.api.notifications.controllers.responses.NotificationResponse;
import com.knowlink.api.notifications.data.enums.NotificationActionStatus;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.data.models.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class NotificationMapper {

    private final Clock clock;

    public NotificationResponse toResponse(Notification notification) {
        LocalDateTime now = LocalDateTime.now(clock);
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getResourceId(),
                notification.getReadAt() != null,
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.getExpiresAt(),
                resolveActionStatus(notification, now));
    }

    private NotificationActionStatus resolveActionStatus(Notification notification, LocalDateTime now) {
        if (notification.getType() != NotificationType.RATING_ENABLED) {
            return NotificationActionStatus.NONE;
        }
        if (notification.getResolvedAt() != null) {
            return NotificationActionStatus.COMPLETED;
        }
        if (notification.getExpiresAt() != null && now.isAfter(notification.getExpiresAt())) {
            return NotificationActionStatus.EXPIRED;
        }
        return NotificationActionStatus.PENDING;
    }
}