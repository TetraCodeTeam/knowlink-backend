package com.knowlink.api.notifications.services.implementations;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.notifications.controllers.responses.NotificationResponse;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.data.models.Notification;
import com.knowlink.api.notifications.repositories.NotificationRepository;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements INotificationService {

    private static final List<NotificationType> SUPPORT_TYPES = List.of(
            NotificationType.SESSION_CLAIM_UPDATE,
            NotificationType.SESSION_CLAIM_COMMENT,
            NotificationType.MATERIAL_REPORT_UPDATE);

    private final NotificationRepository notificationRepository;
    private final IUserRepository userRepository;

    @Override
    @Transactional
    public void createSupportNotification(
            UUID recipientUserId,
            NotificationType type,
            UUID resourceId,
            String title,
            String message) {
        User recipient = userRepository.findById(recipientUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "USER_NOT_FOUND",
                        "El usuario no existe.",
                        "Notification recipient not found: " + recipientUserId));

        Notification notification = Notification.builder()
                .recipient(recipient)
                .type(type)
                .resourceId(resourceId)
                .title(title)
                .message(message)
                .build();
        notificationRepository.save(notification);
        log.info("Support notification created for user {} with type {}", recipientUserId, type);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(UUID userId) {
        return notificationRepository.findByRecipient_UserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnreadForUser(UUID userId) {
        return countDistinctUnreadItems(notificationRepository.findByRecipient_UserIdAndReadAtIsNull(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnreadSupportForUser(UUID userId) {
        return notificationRepository.findByRecipient_UserIdAndTypeInAndReadAtIsNull(userId, SUPPORT_TYPES).stream()
                .map(notification -> {
                    String resourceType = switch (notification.getType()) {
                        case SESSION_CLAIM_UPDATE, SESSION_CLAIM_COMMENT -> "SESSION_CLAIM";
                        case MATERIAL_REPORT_UPDATE -> "MATERIAL";
                    };
                    return resourceType + ":" + notification.getResourceId();
                })
                .collect(Collectors.toSet())
                .size();
    }

    @Override
    @Transactional
    public void markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository
                .findByNotificationIdAndRecipient_UserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "NOTIFICATION_NOT_FOUND",
                        "La notificación no existe.",
                        "Notification not found for user " + userId + ": " + notificationId));

        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now(AppTimeZone.ZONE));
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID userId) {
        List<Notification> unreadNotifications =
                notificationRepository.findByRecipient_UserIdAndReadAtIsNull(userId);
        if (unreadNotifications.isEmpty()) {
            return;
        }

        LocalDateTime readAt = LocalDateTime.now(AppTimeZone.ZONE);
        unreadNotifications.forEach(notification -> notification.setReadAt(readAt));
        notificationRepository.saveAll(unreadNotifications);
    }

    @Override
    @Transactional
    public void markSupportAsRead(UUID userId) {
        List<Notification> unreadNotifications = notificationRepository
                .findByRecipient_UserIdAndTypeInAndReadAtIsNull(userId, SUPPORT_TYPES);
        if (unreadNotifications.isEmpty()) {
            return;
        }

        LocalDateTime readAt = LocalDateTime.now(AppTimeZone.ZONE);
        unreadNotifications.forEach(notification -> notification.setReadAt(readAt));
        notificationRepository.saveAll(unreadNotifications);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getType(),
                notification.getResourceId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.getReadAt() != null);
    }

    private long countDistinctUnreadItems(List<Notification> notifications) {
        return notifications.stream()
                .map(notification -> notification.getType() + ":" + notification.getResourceId())
                .collect(Collectors.toSet())
                .size();
    }
}
