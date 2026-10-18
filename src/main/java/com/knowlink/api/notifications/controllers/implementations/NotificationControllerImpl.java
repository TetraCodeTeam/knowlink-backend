package com.knowlink.api.notifications.controllers.implementations;

import com.knowlink.api.notifications.controllers.interfaces.INotificationController;
import com.knowlink.api.notifications.controllers.responses.NotificationResponse;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.security.models.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class NotificationControllerImpl implements INotificationController {

    private final INotificationService notificationService;

    @Override
    public List<NotificationResponse> list(UserPrincipal principal) {
        return notificationService.listForUser(principal.getUser().getUserId());
    }

    @Override
    public Map<String, Long> unreadCount(UserPrincipal principal) {
        return Map.of("unreadCount", notificationService.countUnreadForUser(principal.getUser().getUserId()));
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
}
