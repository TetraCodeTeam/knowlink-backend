package com.knowlink.api.notifications.repositories;

import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.data.models.Notification;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private IUserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private User recipient;
    private User otherRecipient;

    @BeforeEach
    void setUp() {
        recipient = persistUser("recipient");
        otherRecipient = persistUser("other-recipient");
    }

    @Test
    @DisplayName("Listado - ordena por createdAt desc y resuelve los empates de forma estable")
    void listing_ordersByCreatedAtDescendingWithStableTieBreak() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 20, 10, 0);
        Notification oldest = persistNotification(recipient, base, null, "oldest");
        Notification tiedA = persistNotification(recipient, base.plusHours(1), null, "tied-a");
        Notification tiedB = persistNotification(recipient, base.plusHours(1), null, "tied-b");
        Notification newest = persistNotification(recipient, base.plusHours(2), null, "newest");

        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"))
                .and(Sort.by(Sort.Order.desc("notificationId"))));

        List<UUID> firstRun = notificationRepository.findByRecipient_UserId(recipient.getUserId(), pageable)
                .getContent().stream().map(Notification::getNotificationId).toList();
        List<UUID> secondRun = notificationRepository.findByRecipient_UserId(recipient.getUserId(), pageable)
                .getContent().stream().map(Notification::getNotificationId).toList();

        assertThat(firstRun).hasSize(4);
        assertThat(firstRun.get(0)).isEqualTo(newest.getNotificationId());
        assertThat(firstRun.get(3)).isEqualTo(oldest.getNotificationId());
        assertThat(firstRun.subList(1, 3))
                .containsExactlyInAnyOrder(tiedA.getNotificationId(), tiedB.getNotificationId());
        assertThat(secondRun).isEqualTo(firstRun);
    }

    @Test
    @DisplayName("unreadOnly - pagina solo las notificaciones sin leer")
    void unreadOnly_returnsOnlyUnreadRows() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 20, 10, 0);
        Notification unread = persistNotification(recipient, base, null, "unread");
        persistNotification(recipient, base.plusMinutes(1), base.plusMinutes(2), "read");

        Page<Notification> page = notificationRepository.findByRecipient_UserIdAndReadAtIsNull(
                recipient.getUserId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getNotificationId()).isEqualTo(unread.getNotificationId());
    }

    @Test
    @DisplayName("readOnly - pagina solo las notificaciones leidas")
    void readOnly_returnsOnlyReadRows() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 20, 10, 0);
        persistNotification(recipient, base, null, "unread");
        Notification read = persistNotification(recipient, base.plusMinutes(1), base.plusMinutes(2), "read");

        Page<Notification> page = notificationRepository.findByRecipient_UserIdAndReadAtIsNotNull(
                recipient.getUserId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getNotificationId()).isEqualTo(read.getNotificationId());
    }

    @Test
    @DisplayName("Conteo - cuenta las filas sin leer del destinatario")
    void countUnread_countsOnlyUnreadRowsOfThatRecipient() {
        persistNotification(recipient, LocalDateTime.of(2026, 10, 20, 9, 0), null, "u1");
        persistNotification(recipient, LocalDateTime.of(2026, 10, 20, 9, 1), null, "u2");
        persistNotification(recipient, LocalDateTime.of(2026, 10, 20, 9, 2),
                LocalDateTime.of(2026, 10, 20, 10, 0), "r1");
        persistNotification(otherRecipient, LocalDateTime.of(2026, 10, 20, 9, 3), null, "other");

        assertThat(notificationRepository.countByRecipient_UserIdAndReadAtIsNull(recipient.getUserId()))
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("Dedupe - la restriccion unica (recipient, dedupeKey) bloquea la fila repetida")
    void uniqueDedupeKey_blocksDuplicateRowForSameRecipient() {
        String dedupeKey = "BOOKING_CONFIRMED:" + UUID.randomUUID();
        persistNotification(recipient, LocalDateTime.of(2026, 10, 20, 9, 0), null, "first", dedupeKey);
        entityManager.flush();

        Notification duplicate = notificationFor(recipient, LocalDateTime.of(2026, 10, 20, 9, 1), null,
                "duplicate", dedupeKey);

        assertThatThrownBy(() -> notificationRepository.saveAndFlush(duplicate))
                .isInstanceOfAny(DataIntegrityViolationException.class, JpaSystemException.class);
    }

    @Test
    @DisplayName("Dedupe - la misma clave otro destinatario y las claves nulas repetidas se permiten")
    void uniqueDedupeKey_allowsSameKeyForAnotherRecipientAndNullKeys() {
        String dedupeKey = "FUNDS_TRANSFERRED:" + UUID.randomUUID();
        persistNotification(recipient, LocalDateTime.of(2026, 10, 20, 9, 0), null, "for-recipient", dedupeKey);
        persistNotification(otherRecipient, LocalDateTime.of(2026, 10, 20, 9, 1), null, "for-other", dedupeKey);
        persistNotification(recipient, LocalDateTime.of(2026, 10, 20, 9, 2), null, "null-key-1", null);
        persistNotification(recipient, LocalDateTime.of(2026, 10, 20, 9, 3), null, "null-key-2", null);
        entityManager.flush();

        assertThat(notificationRepository.existsByRecipient_UserIdAndDedupeKey(recipient.getUserId(), dedupeKey))
                .isTrue();
        assertThat(notificationRepository.countByRecipient_UserIdAndReadAtIsNull(recipient.getUserId()))
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("existsBy - distingue claves existentes de inexistentes")
    void existsByDedupeKey_returnsFalseWhenMissing() {
        persistNotification(recipient, LocalDateTime.of(2026, 10, 20, 9, 0), null, "known", "known-key");
        entityManager.flush();

        assertThat(notificationRepository.existsByRecipient_UserIdAndDedupeKey(recipient.getUserId(), "known-key"))
                .isTrue();
        assertThat(notificationRepository.existsByRecipient_UserIdAndDedupeKey(recipient.getUserId(), "missing-key"))
                .isFalse();
    }

    @Test
    @DisplayName("Por id - solo encuentra la notificacion cuando le pertenece al usuario")
    void findByIdAndRecipient_filtersByOwner() {
        Notification notification = persistNotification(recipient, LocalDateTime.of(2026, 10, 20, 9, 0), null,
                "owned");

        assertThat(notificationRepository.findByNotificationIdAndRecipient_UserId(
                notification.getNotificationId(), recipient.getUserId())).isPresent();
        assertThat(notificationRepository.findByNotificationIdAndRecipient_UserId(
                notification.getNotificationId(), otherRecipient.getUserId())).isEmpty();
    }

    private User persistUser(String prefix) {
        return userRepository.saveAndFlush(User.builder()
                .fullName("Repository User")
                .email(prefix + "." + UUID.randomUUID() + "@test.com")
                .password("hashed")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private Notification persistNotification(User user, LocalDateTime createdAt, LocalDateTime readAt, String message) {
        return persistNotification(user, createdAt, readAt, message, null);
    }

    private Notification persistNotification(User user, LocalDateTime createdAt, LocalDateTime readAt,
            String message, String dedupeKey) {
        Notification saved = notificationRepository.saveAndFlush(
                notificationFor(user, createdAt, readAt, message, dedupeKey));
        entityManager.clear();
        return saved;
    }

    private Notification notificationFor(User user, LocalDateTime createdAt, LocalDateTime readAt, String message,
            String dedupeKey) {
        return Notification.builder()
                .recipient(user)
                .type(NotificationType.BOOKING_CONFIRMED)
                .message(message)
                .resourceId(UUID.randomUUID())
                .dedupeKey(dedupeKey)
                .createdAt(createdAt)
                .readAt(readAt)
                .build();
    }
}