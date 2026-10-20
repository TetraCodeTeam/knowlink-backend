package com.knowlink.api.notifications.services;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.notifications.controllers.responses.UnreadCountResponse;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.data.mappers.NotificationMapper;
import com.knowlink.api.notifications.data.models.Notification;
import com.knowlink.api.notifications.repositories.NotificationRepository;
import com.knowlink.api.notifications.services.implementations.NotificationServiceImpl;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private IUserRepository userRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl service;

    private final UUID recipientId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @Test
    @DisplayName("notifyUser - crea la notificación con destinatario, tipo, mensaje, referencia y dedupeKey")
    void notifyUser_persistsNotificationWithAllFields() {
        User recipient = user(recipientId, Role.STUDENT, AccountStatus.ACTIVE);
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));
        String dedupeKey = "BOOKING_CONFIRMED:" + bookingId;

        service.notifyUser(recipientId, NotificationType.BOOKING_CONFIRMED,
                "Tu reserva fue confirmada.", bookingId, dedupeKey);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getRecipient()).isSameAs(recipient);
        assertThat(saved.getType()).isEqualTo(NotificationType.BOOKING_CONFIRMED);
        assertThat(saved.getMessage()).isEqualTo("Tu reserva fue confirmada.");
        assertThat(saved.getResourceId()).isEqualTo(bookingId);
        assertThat(saved.getDedupeKey()).isEqualTo(dedupeKey);
        assertThat(saved.getTitle()).isNull();
        assertThat(saved.getReadAt()).isNull();
    }

    @Test
    @DisplayName("notifyUser - ya existe (recipient, dedupeKey): no crea una segunda")
    void notifyUser_duplicatedKey_doesNothing() {
        User recipient = user(recipientId, Role.STUDENT, AccountStatus.ACTIVE);
        String dedupeKey = "BOOKING_CONFIRMED:" + bookingId;
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));
        when(notificationRepository.existsByRecipient_UserIdAndDedupeKey(recipientId, dedupeKey)).thenReturn(true);

        service.notifyUser(recipientId, NotificationType.BOOKING_CONFIRMED, "msg", bookingId, dedupeKey);

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("notifyUser - carrera contra la restricción única: la violación se traga")
    void notifyUser_uniqueConstraintRace_isSwallowed() {
        User recipient = user(recipientId, Role.STUDENT, AccountStatus.ACTIVE);
        String dedupeKey = "BOOKING_CONFIRMED:" + bookingId;
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));
        when(notificationRepository.existsByRecipient_UserIdAndDedupeKey(recipientId, dedupeKey)).thenReturn(false);
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        service.notifyUser(recipientId, NotificationType.BOOKING_CONFIRMED, "msg", bookingId, dedupeKey);

        verify(notificationRepository).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("notifyUser - violación de integridad sin dedupeKey: se propaga")
    void notifyUser_integrityViolationWithoutDedupeKey_isRethrown() {
        User recipient = user(recipientId, Role.STUDENT, AccountStatus.ACTIVE);
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("broken row"));

        assertThatThrownBy(() -> service.notifyUser(recipientId, NotificationType.BOOKING_CONFIRMED,
                "msg", bookingId, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("notifyUser - destinatario inexistente: USER_NOT_FOUND")
    void notifyUser_unknownRecipient_throwsNotFound() {
        when(userRepository.findById(recipientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.notifyUser(recipientId, NotificationType.BOOKING_CONFIRMED,
                "msg", bookingId, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(e -> ((ResourceNotFoundException) e).getErrorCode())
                .isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("notifyAdmins - crea una notificación por cada admin activo")
    void notifyAdmins_notifiesEveryActiveAdmin() {
        User firstAdmin = user(UUID.randomUUID(), Role.ADMIN, AccountStatus.ACTIVE);
        User secondAdmin = user(UUID.randomUUID(), Role.ADMIN, AccountStatus.ACTIVE);
        when(userRepository.findAllByRoleAndAccountStatus(Role.ADMIN, AccountStatus.ACTIVE))
                .thenReturn(List.of(firstAdmin, secondAdmin));
        String dedupeKey = "ADMIN_VALIDATION_REQUEST_PENDING:" + bookingId;

        service.notifyAdmins(NotificationType.ADMIN_VALIDATION_REQUEST_PENDING,
                "Hay una solicitud nueva.", bookingId, dedupeKey);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(notification -> notification.getRecipient().getUserId())
                .containsExactlyInAnyOrder(firstAdmin.getUserId(), secondAdmin.getUserId());
        assertThat(captor.getAllValues())
                .allSatisfy(notification -> {
                    assertThat(notification.getDedupeKey()).isEqualTo(dedupeKey);
                    assertThat(notification.getType()).isEqualTo(NotificationType.ADMIN_VALIDATION_REQUEST_PENDING);
                });
    }

    @Test
    @DisplayName("notifyAdmins - sin admins activos: no es error y no crea nada")
    void notifyAdmins_withoutAdmins_doesNothing() {
        when(userRepository.findAllByRoleAndAccountStatus(Role.ADMIN, AccountStatus.ACTIVE))
                .thenReturn(List.of());

        service.notifyAdmins(NotificationType.ADMIN_MATERIAL_REPORT_RECEIVED, "msg", bookingId, "key");

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("markAsRead - notificación propia sin leer: informa readAt y guarda")
    void markAsRead_unreadNotification_setsReadAt() {
        Notification notification = Notification.builder()
                .notificationId(UUID.randomUUID())
                .recipient(user(recipientId, Role.STUDENT, AccountStatus.ACTIVE))
                .type(NotificationType.BOOKING_CONFIRMED)
                .message("msg")
                .build();
        when(notificationRepository.findByNotificationIdAndRecipient_UserId(
                notification.getNotificationId(), recipientId)).thenReturn(Optional.of(notification));

        service.markAsRead(recipientId, notification.getNotificationId());

        assertThat(notification.getReadAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    @DisplayName("markAsRead - notificación ya leída: es idempotente y no vuelve a guardar")
    void markAsRead_alreadyRead_doesNothing() {
        Notification notification = Notification.builder()
                .notificationId(UUID.randomUUID())
                .recipient(user(recipientId, Role.STUDENT, AccountStatus.ACTIVE))
                .type(NotificationType.BOOKING_CONFIRMED)
                .message("msg")
                .readAt(java.time.LocalDateTime.of(2026, 10, 1, 10, 0))
                .build();
        when(notificationRepository.findByNotificationIdAndRecipient_UserId(
                notification.getNotificationId(), recipientId)).thenReturn(Optional.of(notification));

        service.markAsRead(recipientId, notification.getNotificationId());

        assertThat(notification.getReadAt()).isEqualTo(java.time.LocalDateTime.of(2026, 10, 1, 10, 0));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("markAsRead - notificación de otro usuario: NOTIFICATION_NOT_FOUND")
    void markAsRead_notificationOfAnotherUser_throwsNotFound() {
        UUID foreignId = UUID.randomUUID();
        when(notificationRepository.findByNotificationIdAndRecipient_UserId(foreignId, recipientId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(recipientId, foreignId))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(e -> ((ResourceNotFoundException) e).getErrorCode())
                .isEqualTo("NOTIFICATION_NOT_FOUND");
    }

    @Test
    @DisplayName("getUnreadCount - devuelve la cantidad de filas sin leer")
    void getUnreadCount_returnsRowAmount() {
        when(notificationRepository.countByRecipient_UserIdAndReadAtIsNull(recipientId)).thenReturn(3L);

        UnreadCountResponse response = service.getUnreadCount(recipientId);

        assertThat(response.count()).isEqualTo(3L);
    }

    @Test
    @DisplayName("getNotifications - recorta el tamanio a 50 y ordena por createdAt y notificationId desc")
    void getNotifications_limitsSizeAndForcesStableSort() {
        when(notificationRepository.findByRecipient_UserId(eq(recipientId), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getNotifications(recipientId, false, PageRequest.of(0, 500));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByRecipient_UserId(eq(recipientId), captor.capture());
        Pageable requested = captor.getValue();
        assertThat(requested.getPageSize()).isEqualTo(50);
        assertThat(requested.getSort()).containsExactly(
                Sort.Order.desc("createdAt"), Sort.Order.desc("notificationId"));
    }


    @Test
    @DisplayName("getNotifications - unreadOnly consulta solo las filas sin leer")
    void getNotifications_unreadOnlyQueriesUnreadRows() {
        when(notificationRepository.findByRecipient_UserIdAndReadAtIsNull(eq(recipientId), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getNotifications(recipientId, true, PageRequest.of(0, 20));

        verify(notificationRepository).findByRecipient_UserIdAndReadAtIsNull(eq(recipientId), any(Pageable.class));
        verify(notificationRepository, never()).findByRecipient_UserId(any(UUID.class), any(Pageable.class));
    }

    private static User user(UUID id, Role role, AccountStatus status) {
        return User.builder()
                .userId(id)
                .fullName("Test User")
                .email(id + "@test.com")
                .password("hashed")
                .role(role)
                .accountStatus(status)
                .build();
    }
}