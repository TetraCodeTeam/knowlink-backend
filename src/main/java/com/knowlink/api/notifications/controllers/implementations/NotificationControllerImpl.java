package com.knowlink.api.notifications.controllers.implementations;

import com.knowlink.api.notifications.controllers.interfaces.INotificationController;
import com.knowlink.api.notifications.controllers.responses.NotificationResponse;
import com.knowlink.api.notifications.controllers.responses.UnreadCountResponse;
import com.knowlink.api.notifications.data.enums.NotificationReadFilter;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.notifications.utils.NotificationConstants;
import com.knowlink.api.shared.responses.PagedResponse;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class NotificationControllerImpl implements INotificationController {

    private final INotificationService notificationService;

    @Override
    public PagedResponse<NotificationResponse> list(UserPrincipal principal, int page, int size,
            NotificationReadFilter status, boolean unreadOnly) {
        NotificationReadFilter readFilter = status != NotificationReadFilter.ALL
                ? status
                : (unreadOnly ? NotificationReadFilter.UNREAD : NotificationReadFilter.ALL);
        return notificationService.getNotifications(
                principal.getUser().getUserId(),
                readFilter,
                pageable(page, size));
    }

    @Override
    public UnreadCountResponse unreadCount(UserPrincipal principal) {
        return notificationService.getUnreadCount(principal.getUser().getUserId());
    }

    @Override
    public Map<String, Long> supportUnreadCount(UserPrincipal principal) {
        return Map.of("unreadCount", notificationService.countUnreadSupportForUser(principal.getUser().getUserId()));
    }

    @Override
    public void markAsRead(UserPrincipal principal, UUID notificationId) {
        notificationService.markAsRead(principal.getUser().getUserId(), notificationId);
    }

    @Override
    public void markAllAsRead(UserPrincipal principal) {
        notificationService.markAllAsRead(principal.getUser().getUserId());
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), size > 0 ? size : NotificationConstants.DEFAULT_PAGE_SIZE);
    }
}