package com.knowlink.api.notifications.controllers.responses;

import com.knowlink.api.notifications.data.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID notificationId,
        NotificationType type,
        String title,
        String message,
        UUID referenceId,
        boolean read,
        LocalDateTime createdAt,
        LocalDateTime readAt) {
}