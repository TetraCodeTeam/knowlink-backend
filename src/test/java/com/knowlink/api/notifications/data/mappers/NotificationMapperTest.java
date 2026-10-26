package com.knowlink.api.notifications.data.mappers;

import com.knowlink.api.notifications.controllers.responses.NotificationResponse;
import com.knowlink.api.notifications.data.enums.NotificationActionStatus;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.data.models.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMapperTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Cordoba");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    private NotificationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new NotificationMapper(CLOCK);
    }

    @Test
    @DisplayName("RATING_ENABLED vigente - actionStatus PENDING y expiresAt informado")
    void ratingEnabledWithinWindow_isPending() {
        LocalDateTime expiresAt = NOW.plusHours(1);
        Notification notification = notification(NotificationType.RATING_ENABLED, expiresAt, null);

        NotificationResponse response = mapper.toResponse(notification);

        assertThat(response.actionStatus()).isEqualTo(NotificationActionStatus.PENDING);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("RATING_ENABLED resuelta - actionStatus COMPLETED")
    void ratingEnabledResolved_isCompleted() {
        Notification notification = notification(NotificationType.RATING_ENABLED, NOW.plusHours(1),
                NOW.minusMinutes(5));

        assertThat(mapper.toResponse(notification).actionStatus())
                .isEqualTo(NotificationActionStatus.COMPLETED);
    }

    @Test
    @DisplayName("RATING_ENABLED vencida - actionStatus EXPIRED")
    void ratingEnabledExpired_isExpired() {
        Notification notification = notification(NotificationType.RATING_ENABLED, NOW.minusMinutes(1), null);

        assertThat(mapper.toResponse(notification).actionStatus())
                .isEqualTo(NotificationActionStatus.EXPIRED);
    }

    @Test
    @DisplayName("RATING_ENABLED en el borde exacto - sigue PENDING")
    void ratingEnabledAtExactDeadline_isPending() {
        Notification notification = notification(NotificationType.RATING_ENABLED, NOW, null);

        assertThat(mapper.toResponse(notification).actionStatus())
                .isEqualTo(NotificationActionStatus.PENDING);
    }

    @Test
    @DisplayName("RATING_ENABLED sin vencimiento (fila vieja) - sigue PENDING")
    void ratingEnabledWithoutExpiry_isPending() {
        Notification notification = notification(NotificationType.RATING_ENABLED, null, null);

        assertThat(mapper.toResponse(notification).actionStatus())
                .isEqualTo(NotificationActionStatus.PENDING);
    }

    @Test
    @DisplayName("Otros tipos - actionStatus NONE")
    void otherTypes_haveNoAction() {
        Notification notification = notification(NotificationType.BOOKING_CONFIRMED, NOW.plusHours(1), null);

        assertThat(mapper.toResponse(notification).actionStatus())
                .isEqualTo(NotificationActionStatus.NONE);
    }

    private Notification notification(NotificationType type, LocalDateTime expiresAt, LocalDateTime resolvedAt) {
        return Notification.builder()
                .notificationId(UUID.randomUUID())
                .type(type)
                .title("title")
                .message("message")
                .createdAt(NOW.minusHours(1))
                .expiresAt(expiresAt)
                .resolvedAt(resolvedAt)
                .build();
    }
}