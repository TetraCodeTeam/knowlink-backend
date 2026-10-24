package com.knowlink.api.notifications.services.implementations;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.notifications.controllers.responses.NotificationResponse;
import com.knowlink.api.notifications.controllers.responses.UnreadCountResponse;
import com.knowlink.api.notifications.data.enums.NotificationReadFilter;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.data.mappers.NotificationMapper;
import com.knowlink.api.notifications.data.models.Notification;
import com.knowlink.api.notifications.repositories.NotificationRepository;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.notifications.utils.NotificationConstants;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.responses.PagedResponse;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

    private static final Sort LISTING_SORT = Sort.by(Sort.Order.desc("createdAt"))
            .and(Sort.by(Sort.Order.desc("notificationId")));

    private final NotificationRepository notificationRepository;
    private final IUserRepository userRepository;
    private final NotificationMapper notificationMapper;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Transactional
    public void createSupportNotification(
            UUID recipientUserId,
            NotificationType type,
            UUID resourceId,
            String title,
            String message) {
        User recipient = findRecipient(recipientUserId);

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
    public void notifyUser(UUID recipientUserId, NotificationType type, String title, String message,
                           UUID referenceId, String dedupeKey) {
        notifyTransactionTemplate().executeWithoutResult(status ->
                createNotification(findRecipient(recipientUserId), type, title, message, referenceId, dedupeKey));
    }

    @Override
    public void notifyAdmins(NotificationType type, String title, String message, UUID referenceId,
                             String dedupeKey) {
        List<User> admins = userRepository.findAllByRoleAndAccountStatus(Role.ADMIN, AccountStatus.ACTIVE);
        if (admins.isEmpty()) {
            log.warn("No active admin users to notify with type {}", type);
            return;
        }
        admins.forEach(admin -> {
            try {
                notifyTransactionTemplate().executeWithoutResult(status ->
                        createNotification(admin, type, title, message, referenceId, dedupeKey));
            } catch (RuntimeException e) {
                log.error("Failed to create notification {} for admin {}", type, admin.getUserId(), e);
            }
        });
    }

    private TransactionTemplate notifyTransactionTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getNotifications(UUID userId, NotificationReadFilter readFilter,
            Pageable pageable) {
        Pageable safePageable = PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                resolvePageSize(pageable.getPageSize()),
                LISTING_SORT);

        Page<Notification> notifications = switch (readFilter) {
            case UNREAD -> notificationRepository.findByRecipient_UserIdAndReadAtIsNull(userId, safePageable);
            case READ -> notificationRepository.findByRecipient_UserIdAndReadAtIsNotNull(userId, safePageable);
            case ALL -> notificationRepository.findByRecipient_UserId(userId, safePageable);
        };

        return PagedResponse.from(notifications.map(notificationMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(UUID userId) {
        return new UnreadCountResponse(notificationRepository.countByRecipient_UserIdAndReadAtIsNull(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnreadSupportForUser(UUID userId) {
        return notificationRepository.findByRecipient_UserIdAndTypeInAndReadAtIsNull(userId, SUPPORT_TYPES).stream()
                .map(notification -> {
                    String resourceType = switch (notification.getType()) {
                        case SESSION_CLAIM_UPDATE, SESSION_CLAIM_COMMENT -> "SESSION_CLAIM";
                        case MATERIAL_REPORT_UPDATE -> "MATERIAL";
                        default -> notification.getType().name();
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
        List<Notification> unreadNotifications = notificationRepository.findByRecipient_UserIdAndReadAtIsNull(userId);
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

    private void createNotification(User recipient, NotificationType type, String title, String message,
                                    UUID referenceId, String dedupeKey) {
        UUID recipientUserId = recipient.getUserId();
        try {
            if (dedupeKey != null
                    && notificationRepository.existsByRecipient_UserIdAndDedupeKey(recipientUserId, dedupeKey)) {
                log.debug("Skipping duplicated notification {} for user {} with key {}", type, recipientUserId,
                        dedupeKey);
                return;
            }

            Notification notification = Notification.builder()
                    .recipient(recipient)
                    .type(type)
                    .title(title)
                    .message(message)
                    .resourceId(referenceId)
                    .dedupeKey(dedupeKey)
                    .createdAt(LocalDateTime.now(AppTimeZone.ZONE))
                    .build();
            notificationRepository.saveAndFlush(notification);
            log.debug("Notification {} created for user {}", type, recipientUserId);
        } catch (DataIntegrityViolationException e) {
            if (dedupeKey == null) {
                throw e;
            }
            log.debug("Duplicated notification {} for user {} with key {} skipped by unique constraint", type,
                    recipientUserId, dedupeKey);
        }
    }

    private User findRecipient(UUID recipientUserId) {
        return userRepository.findById(recipientUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "USER_NOT_FOUND",
                        "El usuario no existe.",
                        "Notification recipient not found: " + recipientUserId));
    }

    private int resolvePageSize(int pageSize) {
        if (pageSize <= 0) {
            return NotificationConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, NotificationConstants.MAX_PAGE_SIZE);
    }
}