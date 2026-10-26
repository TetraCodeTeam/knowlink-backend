package com.knowlink.api.notifications.listeners;

import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.ratings.events.RatingSubmittedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RatingNotificationListenerTest {

    @Mock
    private INotificationService notificationService;

    @InjectMocks
    private RatingNotificationListener listener;

    @Test
    @DisplayName("Calificación enviada - resuelve solo la notificación del usuario que calificó")
    void handleRatingSubmitted_resolvesNotificationOfTheRater() {
        UUID bookingId = UUID.randomUUID();
        UUID raterUserId = UUID.randomUUID();

        listener.handleRatingSubmitted(new RatingSubmittedEvent(bookingId, raterUserId));

        verify(notificationService).resolveNotification(
                raterUserId, NotificationType.RATING_ENABLED, bookingId);
    }

    @Test
    @DisplayName("Fallo al resolver - no se propaga")
    void handleRatingSubmitted_failure_isSwallowed() {
        UUID bookingId = UUID.randomUUID();
        UUID raterUserId = UUID.randomUUID();
        doThrow(new RuntimeException("db down")).when(notificationService)
                .resolveNotification(raterUserId, NotificationType.RATING_ENABLED, bookingId);

        listener.handleRatingSubmitted(new RatingSubmittedEvent(bookingId, raterUserId));

        verify(notificationService).resolveNotification(
                raterUserId, NotificationType.RATING_ENABLED, bookingId);
    }
}