package com.knowlink.api.notifications.data.mappers;

import com.knowlink.api.notifications.controllers.responses.NotificationResponse;
import com.knowlink.api.notifications.data.models.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getResourceId(),
                notification.getReadAt() != null,
                notification.getCreatedAt(),
                notification.getReadAt());
    }
}