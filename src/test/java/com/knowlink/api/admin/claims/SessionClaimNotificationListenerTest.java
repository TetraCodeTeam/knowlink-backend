package com.knowlink.api.admin.claims;

import com.knowlink.api.admin.claims.data.enums.ClaimFavoredParty;
import com.knowlink.api.admin.claims.events.SessionClaimResolvedEvent;
import com.knowlink.api.admin.claims.listeners.SessionClaimNotificationListener;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.users.services.interfaces.IEmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionClaimNotificationListenerTest {

    @Mock
    private INotificationService notificationService;

    @Mock
    private IEmailService emailService;

    @Mock
    private IUserRepository userRepository;

    private SessionClaimNotificationListener listener;

    private UUID claimId;
    private UUID tutorId;
    private UUID studentId;
    private User tutor;
    private User student;

    @BeforeEach
    void setUp() {
        listener = new SessionClaimNotificationListener(notificationService, emailService, userRepository);
        claimId = UUID.randomUUID();
        tutorId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        tutor = User.builder().userId(tutorId).fullName("Tutor").email("tutor@test.com").role(Role.TUTOR).build();
        student = User.builder().userId(studentId).fullName("Alumno").email("alumno@test.com").role(Role.STUDENT).build();
        when(userRepository.findById(tutorId)).thenReturn(Optional.of(tutor));
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
    }

    private SessionClaimResolvedEvent event(ClaimFavoredParty favoredParty, BigDecimal movedAmount) {
        return new SessionClaimResolvedEvent(
                claimId,
                UUID.randomUUID(),
                tutorId,
                studentId,
                favoredParty,
                "El tutor no se conectó",
                "Algoritmos",
                LocalDate.of(2026, 10, 20),
                movedAmount);
    }

    @Test
    @DisplayName("Ambas partes reciben notificación in-app y mail sin datos de contacto de la contraparte")
    void notifiesBothParties() {
        listener.handleSessionClaimResolved(event(ClaimFavoredParty.STUDENT, new BigDecimal("1000.00")));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyUser(
                eq(tutorId), eq(Role.TUTOR), eq(NotificationType.SESSION_CLAIM_UPDATE),
                eq("Reclamo resuelto"), messageCaptor.capture(), eq(claimId),
                eq("session-claim-resolved:" + claimId + ":" + tutorId));
        verify(notificationService).notifyUser(
                eq(studentId), eq(Role.STUDENT), eq(NotificationType.SESSION_CLAIM_UPDATE),
                anyString(), anyString(), eq(claimId),
                eq("session-claim-resolved:" + claimId + ":" + studentId));

        String message = messageCaptor.getValue();
        assertThat(message).contains("Algoritmos");
        assertThat(message).contains("20/10/2026");
        assertThat(message).contains("a favor del alumno");
        assertThat(message).contains("El tutor no se conectó");
        assertThat(message).contains("reintegrados al alumno");
        assertThat(message).doesNotContain("tutor@test.com");
        assertThat(message).doesNotContain("alumno@test.com");

        ArgumentCaptor<String> emailBodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAdminNotificationEmail(eq(tutor), anyString(), anyString(), emailBodyCaptor.capture());
        verify(emailService).sendAdminNotificationEmail(eq(student), anyString(), anyString(), any());
        assertThat(emailBodyCaptor.getValue()).doesNotContain("alumno@test.com");
    }

    @Test
    @DisplayName("Sesión gratuita: el mensaje indica que no había fondos retenidos")
    void freeSessionMessageMentionsNoFunds() {
        listener.handleSessionClaimResolved(event(ClaimFavoredParty.TUTOR, BigDecimal.ZERO));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyUser(
                eq(tutorId), eq(Role.TUTOR), eq(NotificationType.SESSION_CLAIM_UPDATE),
                anyString(), messageCaptor.capture(), eq(claimId), anyString());

        assertThat(messageCaptor.getValue()).contains("No había fondos retenidos");
    }

    @Test
    @DisplayName("Una falla de mail no propaga la excepción")
    void emailFailureDoesNotPropagate() {
        doThrow(new RuntimeException("smtp down"))
                .when(emailService).sendAdminNotificationEmail(any(), anyString(), anyString(), anyString());

        assertThatCode(() -> listener.handleSessionClaimResolved(
                event(ClaimFavoredParty.TUTOR, new BigDecimal("970.00"))))
                .doesNotThrowAnyException();
    }
}