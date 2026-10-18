package com.knowlink.api.notifications.controllers.responses;

import com.knowlink.api.notifications.data.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        UUID resourceId,
        String title,
        String message,
        LocalDateTime createdAt,
        LocalDateTime readAt,
        boolean read) {
}
