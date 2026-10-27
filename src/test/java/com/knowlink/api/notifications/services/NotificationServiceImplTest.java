package com.knowlink.api.notifications.services;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.notifications.controllers.responses.UnreadCountResponse;
import com.knowlink.api.notifications.data.enums.NotificationReadFilter;
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
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private NotificationServiceImpl service;

    private final UUID recipientId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @Test
    @DisplayName("notifyUser - crea la notificación con destinatario, rol, tipo, mensaje, referencia y dedupeKey")
    void notifyUser_persistsNotificationWithAllFields() {
        User recipient = user(recipientId, Role.TUTOR, AccountStatus.ACTIVE);
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));
        String dedupeKey = "BOOKING_CONFIRMED:" + bookingId;

        service.notifyUser(recipientId, Role.TUTOR, NotificationType.BOOKING_CONFIRMED, "Reserva confirmada",
                "Tu reserva fue confirmada.", bookingId, dedupeKey);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getRecipient()).isSameAs(recipient);
        assertThat(saved.getRecipientRole()).isEqualTo(Role.TUTOR);
        assertThat(saved.getType()).isEqualTo(NotificationType.BOOKING_CONFIRMED);
        assertThat(saved.getMessage()).isEqualTo("Tu reserva fue confirmada.");
        assertThat(saved.getResourceId()).isEqualTo(bookingId);
        assertThat(saved.getDedupeKey()).isEqualTo(dedupeKey);
        assertThat(saved.getTitle()).isEqualTo("Reserva confirmada");
        assertThat(saved.getReadAt()).isNull();
    }

    @Test
    @DisplayName("notifyUser - ya existe (recipient, dedupeKey): no crea una segunda")
    void notifyUser_duplicatedKey_doesNothing() {
        User recipient = user(recipientId, Role.STUDENT, AccountStatus.ACTIVE);
        String dedupeKey = "BOOKING_CONFIRMED:" + bookingId;
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));
        when(notificationRepository.existsByRecipient_UserIdAndDedupeKey(recipientId, dedupeKey)).thenReturn(true);

        service.notifyUser(recipientId, Role.STUDENT, NotificationType.BOOKING_CONFIRMED, "title", "msg", bookingId,
                dedupeKey);

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

        service.notifyUser(recipientId, Role.STUDENT, NotificationType.BOOKING_CONFIRMED, "title", "msg", bookingId,
                dedupeKey);

        verify(notificationRepository).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("notifyUser - violación de integridad sin dedupeKey: se propaga")
    void notifyUser_integrityViolationWithoutDedupeKey_isRethrown() {
        User recipient = user(recipientId, Role.STUDENT, AccountStatus.ACTIVE);
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("broken row"));

        assertThatThrownBy(() -> service.notifyUser(recipientId, Role.STUDENT, NotificationType.BOOKING_CONFIRMED,
                "title", "msg", bookingId, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("notifyUser - destinatario inexistente: USER_NOT_FOUND")
    void notifyUser_unknownRecipient_throwsNotFound() {
        when(userRepository.findById(recipientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.notifyUser(recipientId, Role.STUDENT, NotificationType.BOOKING_CONFIRMED,
                "title", "msg", bookingId, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(e -> ((ResourceNotFoundException) e).getErrorCode())
                .isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("notifyAdmins - crea una notificación ADMIN por cada admin activo")
    void notifyAdmins_notifiesEveryActiveAdmin() {
        User firstAdmin = user(UUID.randomUUID(), Role.ADMIN, AccountStatus.ACTIVE);
        User secondAdmin = user(UUID.randomUUID(), Role.ADMIN, AccountStatus.ACTIVE);
        when(userRepository.findAllByRoleAndAccountStatus(Role.ADMIN, AccountStatus.ACTIVE))
                .thenReturn(List.of(firstAdmin, secondAdmin));
        String dedupeKey = "ADMIN_VALIDATION_REQUEST_PENDING:" + bookingId;

        service.notifyAdmins(NotificationType.ADMIN_VALIDATION_REQUEST_PENDING, "Validacion pendiente",
                "Hay una solicitud nueva.", bookingId, dedupeKey);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(notification -> notification.getRecipient().getUserId())
                .containsExactlyInAnyOrder(firstAdmin.getUserId(), secondAdmin.getUserId());
        assertThat(captor.getAllValues())
                .allSatisfy(notification -> {
                    assertThat(notification.getRecipientRole()).isEqualTo(Role.ADMIN);
                    assertThat(notification.getDedupeKey()).isEqualTo(dedupeKey);
                    assertThat(notification.getType()).isEqualTo(NotificationType.ADMIN_VALIDATION_REQUEST_PENDING);
                });
    }

    @Test
    @DisplayName("notifyAdmins - sin admins activos: no es error y no crea nada")
    void notifyAdmins_withoutAdmins_doesNothing() {
        when(userRepository.findAllByRoleAndAccountStatus(Role.ADMIN, AccountStatus.ACTIVE))
                .thenReturn(List.of());

        service.notifyAdmins(NotificationType.ADMIN_MATERIAL_REPORT_RECEIVED, "title", "msg", bookingId, "key");

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("notifyAdmins - cada destinatario tiene su transaccion: si uno falla, los demas se crean")
    void notifyAdmins_failureOnOneRecipient_doesNotBlockTheRest() {
        User failingAdmin = user(UUID.randomUUID(), Role.ADMIN, AccountStatus.ACTIVE);
        User healthyAdmin = user(UUID.randomUUID(), Role.ADMIN, AccountStatus.ACTIVE);
        when(userRepository.findAllByRoleAndAccountStatus(Role.ADMIN, AccountStatus.ACTIVE))
                .thenReturn(List.of(failingAdmin, healthyAdmin));
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .doAnswer(invocation -> invocation.getArgument(0))
                .when(notificationRepository).saveAndFlush(any(Notification.class));

        service.notifyAdmins(NotificationType.ADMIN_MATERIAL_REPORT_RECEIVED, "Reporte", "Mensaje", bookingId,
                "ADMIN_MATERIAL_REPORT_RECEIVED:" + bookingId);

        verify(notificationRepository, org.mockito.Mockito.times(2)).saveAndFlush(any(Notification.class));
        verify(transactionManager, org.mockito.Mockito.times(2)).getTransaction(any());
        verify(transactionManager).commit(any());
        verify(transactionManager).rollback(any());
    }

    @Test
    @DisplayName("markAsRead - notificación propia sin leer: informa readAt y guarda")
    void markAsRead_unreadNotification_setsReadAt() {
        Notification notification = Notification.builder()
                .notificationId(UUID.randomUUID())
                .recipient(user(recipientId, Role.STUDENT, AccountStatus.ACTIVE))
                .recipientRole(Role.STUDENT)
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
                .recipientRole(Role.STUDENT)
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
    @DisplayName("getUnreadCount - devuelve el conteo del rol activo y del otro rol")
    void getUnreadCount_returnsActiveRoleAndOtherRoleCounts() {
        when(notificationRepository.countByRecipient_UserIdAndRecipientRoleAndReadAtIsNull(recipientId, Role.STUDENT))
                .thenReturn(3L);
        when(notificationRepository.countByRecipient_UserIdAndRecipientRoleAndReadAtIsNull(recipientId, Role.TUTOR))
                .thenReturn(2L);

        UnreadCountResponse response = service.getUnreadCount(recipientId, Role.STUDENT);

        assertThat(response.count()).isEqualTo(3L);
        assertThat(response.otherRoleCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getUnreadCount - un admin no tiene otro rol asociado")
    void getUnreadCount_adminHasNoOtherRoleCount() {
        when(notificationRepository.countByRecipient_UserIdAndRecipientRoleAndReadAtIsNull(recipientId, Role.ADMIN))
                .thenReturn(5L);

        UnreadCountResponse response = service.getUnreadCount(recipientId, Role.ADMIN);

        assertThat(response.count()).isEqualTo(5L);
        assertThat(response.otherRoleCount()).isZero();
        verify(notificationRepository, never())
                .countByRecipient_UserIdAndRecipientRoleAndReadAtIsNull(recipientId, Role.STUDENT);
    }

    @Test
    @DisplayName("getNotifications - recorta el tamanio a 50 y ordena por createdAt y notificationId desc")
    void getNotifications_limitsSizeAndForcesStableSort() {
        when(notificationRepository.findByRecipient_UserIdAndRecipientRole(eq(recipientId), eq(Role.STUDENT),
                any(Pageable.class))).thenReturn(Page.empty());

        service.getNotifications(recipientId, Role.STUDENT, NotificationReadFilter.ALL, PageRequest.of(0, 500));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByRecipient_UserIdAndRecipientRole(eq(recipientId), eq(Role.STUDENT),
                captor.capture());
        Pageable requested = captor.getValue();
        assertThat(requested.getPageSize()).isEqualTo(50);
        assertThat(requested.getSort()).containsExactly(
                Sort.Order.desc("createdAt"), Sort.Order.desc("notificationId"));
    }

    @Test
    @DisplayName("getNotifications - filtro UNREAD consulta solo las filas sin leer del rol activo")
    void getNotifications_unreadFilterQueriesUnreadRows() {
        when(notificationRepository.findByRecipient_UserIdAndRecipientRoleAndReadAtIsNull(
                eq(recipientId), eq(Role.STUDENT), any(Pageable.class))).thenReturn(Page.empty());

        service.getNotifications(recipientId, Role.STUDENT, NotificationReadFilter.UNREAD, PageRequest.of(0, 20));

        verify(notificationRepository).findByRecipient_UserIdAndRecipientRoleAndReadAtIsNull(
                eq(recipientId), eq(Role.STUDENT), any(Pageable.class));
        verify(notificationRepository, never()).findByRecipient_UserIdAndRecipientRole(any(UUID.class), any(),
                any(Pageable.class));
    }

    @Test
    @DisplayName("getNotifications - filtro READ consulta solo las filas leidas del rol activo")
    void getNotifications_readFilterQueriesReadRows() {
        when(notificationRepository.findByRecipient_UserIdAndRecipientRoleAndReadAtIsNotNull(
                eq(recipientId), eq(Role.TUTOR), any(Pageable.class))).thenReturn(Page.empty());

        service.getNotifications(recipientId, Role.TUTOR, NotificationReadFilter.READ, PageRequest.of(0, 20));

        verify(notificationRepository).findByRecipient_UserIdAndRecipientRoleAndReadAtIsNotNull(
                eq(recipientId), eq(Role.TUTOR), any(Pageable.class));
        verify(notificationRepository, never()).findByRecipient_UserIdAndRecipientRole(any(UUID.class), any(),
                any(Pageable.class));
    }

    @Test
    @DisplayName("markAllAsRead - solo marca las notificaciones del rol activo")
    void markAllAsRead_onlyMarksActiveRoleNotifications() {
        Notification unread = Notification.builder()
                .notificationId(UUID.randomUUID())
                .recipient(user(recipientId, Role.STUDENT, AccountStatus.ACTIVE))
                .recipientRole(Role.STUDENT)
                .type(NotificationType.BOOKING_CONFIRMED)
                .message("msg")
                .build();
        when(notificationRepository.findByRecipient_UserIdAndRecipientRoleAndReadAtIsNull(
                recipientId, Role.STUDENT)).thenReturn(List.of(unread));

        service.markAllAsRead(recipientId, Role.STUDENT);

        assertThat(unread.getReadAt()).isNotNull();
        verify(notificationRepository).saveAll(List.of(unread));
        verify(notificationRepository, never()).findByRecipient_UserIdAndRecipientRoleAndReadAtIsNull(
                recipientId, Role.TUTOR);
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