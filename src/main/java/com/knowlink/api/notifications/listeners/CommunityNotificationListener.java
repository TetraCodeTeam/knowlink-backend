package com.knowlink.api.notifications.listeners;

import com.knowlink.api.community.events.CommunityReplyCreatedEvent;
import com.knowlink.api.community.utils.CommunityConstants;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommunityNotificationListener {

    private final INotificationService notificationService;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReplyCreated(CommunityReplyCreatedEvent event) {
        // Responder la propia publicación no genera aviso.
        if (event.replierId().equals(event.postAuthorId())) {
            return;
        }
        try {
            notificationService.notifyUser(
                    event.postAuthorId(),
                    event.postAuthorRole(),
                    NotificationType.COMMUNITY_REPLY,
                    CommunityConstants.REPLY_NOTIFICATION_TITLE,
                    event.replierName() + " respondió en \"" + abbreviate(event.postTitle()) + "\".",
                    event.postId(),
                    "community-reply:" + event.replyId());
        } catch (Exception e) {
            log.error("Failed to notify reply {} on post {}: {}", event.replyId(), event.postId(), e.getMessage(), e);
        }
    }

    private String abbreviate(String title) {
        int max = CommunityConstants.NOTIFICATION_POST_TITLE_LENGTH;
        return title.length() <= max ? title : title.substring(0, max).stripTrailing() + "…";
    }
}
