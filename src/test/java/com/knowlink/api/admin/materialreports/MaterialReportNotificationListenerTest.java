package com.knowlink.api.admin.materialreports;

import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.events.MaterialReportResolvedEvent;
import com.knowlink.api.admin.materialreports.listeners.MaterialReportNotificationListener;
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaterialReportNotificationListenerTest {

    @Mock
    private INotificationService notificationService;

    @Mock
    private IEmailService emailService;

    @Mock
    private IUserRepository userRepository;

    private MaterialReportNotificationListener listener;

    private UUID caseId;
    private UUID tutorId;
    private UUID firstReporterId;
    private UUID secondReporterId;
    private User tutor;
    private User firstReporter;
    private User secondReporter;

    @BeforeEach
    void setUp() {
        listener = new MaterialReportNotificationListener(notificationService, emailService, userRepository);
        caseId = UUID.randomUUID();
        tutorId = UUID.randomUUID();
        firstReporterId = UUID.randomUUID();
        secondReporterId = UUID.randomUUID();
        tutor = User.builder().userId(tutorId).fullName("Tutor").email("tutor@test.com").role(Role.TUTOR).build();
        firstReporter = User.builder().userId(firstReporterId).fullName("Alumno Uno")
                .email("alumno1@test.com").role(Role.STUDENT).build();
        secondReporter = User.builder().userId(secondReporterId).fullName("Alumno Dos")
                .email("alumno2@test.com").role(Role.STUDENT).build();
        when(userRepository.findById(tutorId)).thenReturn(Optional.of(tutor));
        when(userRepository.findById(firstReporterId)).thenReturn(Optional.of(firstReporter));
        when(userRepository.findById(secondReporterId)).thenReturn(Optional.of(secondReporter));
    }

    @Test
    @DisplayName("Material retirado: el tutor recibe el motivo y los denunciantes no ven identidades ajenas")
    void removedCase_notifiesTutorAndReporters() {
        listener.handleMaterialReportResolved(event(MaterialReportCaseStatus.REMOVED, "Copyright",
                Set.of(firstReporterId, secondReporterId)));

        ArgumentCaptor<String> tutorMessage = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyUser(
                eq(tutorId), eq(Role.TUTOR), eq(NotificationType.MATERIAL_REPORT_PROCESSED),
                eq("Material retirado"), tutorMessage.capture(), eq(caseId),
                eq("material-report-resolved:" + caseId + ":" + tutorId));

        assertThat(tutorMessage.getValue()).contains("material.pdf");
        assertThat(tutorMessage.getValue()).contains("Copyright");
        assertThat(tutorMessage.getValue()).doesNotContain("alumno1@test.com", "alumno2@test.com");

        ArgumentCaptor<String> reporterMessage = ArgumentCaptor.forClass(String.class);
        verify(notificationService, times(2)).notifyUser(
                any(), eq(Role.STUDENT), eq(NotificationType.MATERIAL_REPORT_PROCESSED),
                eq("Resultado de tu reporte"), reporterMessage.capture(), eq(caseId), anyString());
        assertThat(reporterMessage.getAllValues())
                .allSatisfy(message -> assertThat(message).contains("el material fue retirado"))
                .allSatisfy(message -> assertThat(message).doesNotContain("alumno1@test.com", "alumno2@test.com"));
    }

    @Test
    @DisplayName("Material desestimado: el tutor conserva el material y los denunciantes reciben el resultado")
    void dismissedCase_keepsMaterialAvailable() {
        listener.handleMaterialReportResolved(event(MaterialReportCaseStatus.DISMISSED, null,
                Set.of(firstReporterId)));

        ArgumentCaptor<String> tutorMessage = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyUser(
                eq(tutorId), eq(Role.TUTOR), any(), eq("Material revisado"),
                tutorMessage.capture(), eq(caseId), anyString());
        assertThat(tutorMessage.getValue()).contains("se mantiene disponible");

        ArgumentCaptor<String> reporterMessage = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyUser(
                eq(firstReporterId), eq(Role.STUDENT), any(), anyString(),
                reporterMessage.capture(), eq(caseId), anyString());
        assertThat(reporterMessage.getValue()).contains("no encontramos infracciones");
    }

    @Test
    @DisplayName("Denunciantes repetidos se notifican una sola vez y el tutor no recibe el mail de denunciante")
    void reportersAreDeduplicatedAndTutorIsExcluded() {
        listener.handleMaterialReportResolved(event(MaterialReportCaseStatus.DISMISSED, null,
                new HashSet<>(List.of(firstReporterId, firstReporterId, tutorId))));

        verify(notificationService, times(1)).notifyUser(
                eq(firstReporterId), eq(Role.STUDENT), any(), anyString(), anyString(), eq(caseId), anyString());
        verify(notificationService, times(1)).notifyUser(
                eq(tutorId), eq(Role.TUTOR), any(), anyString(), anyString(), eq(caseId), anyString());
        verify(notificationService, times(2)).notifyUser(
                any(), any(), any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Sin denunciantes solo se notifica al tutor")
    void withoutReporters_onlyTutorIsNotified() {
        listener.handleMaterialReportResolved(event(MaterialReportCaseStatus.DISMISSED, null, Set.of()));

        verify(notificationService, times(1)).notifyUser(
                any(), any(), any(), anyString(), anyString(), any(), anyString());
        verify(emailService, times(1)).sendAdminNotificationEmail(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Una falla de mail no propaga la excepción")
    void emailFailureDoesNotPropagate() {
        doThrow(new RuntimeException("smtp down"))
                .when(emailService).sendAdminNotificationEmail(any(), anyString(), anyString(), anyString());

        assertThatCode(() -> listener.handleMaterialReportResolved(
                event(MaterialReportCaseStatus.REMOVED, "Copyright", Set.of(firstReporterId))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Una falla de notificación in-app no corta las demás notificaciones")
    void inAppFailureDoesNotStopOtherRecipients() {
        doThrow(new RuntimeException("notification down"))
                .when(notificationService).notifyUser(eq(tutorId), any(), any(), anyString(), anyString(), any(), anyString());

        assertThatCode(() -> listener.handleMaterialReportResolved(
                event(MaterialReportCaseStatus.DISMISSED, null, Set.of(firstReporterId))))
                .doesNotThrowAnyException();

        verify(notificationService).notifyUser(
                eq(firstReporterId), eq(Role.STUDENT), any(), anyString(), anyString(), eq(caseId), anyString());
    }

    @Test
    @DisplayName("Un tutor inexistente no bloquea la notificación a los denunciantes")
    void missingTutor_stillNotifiesReporters() {
        when(userRepository.findById(tutorId)).thenReturn(Optional.empty());

        listener.handleMaterialReportResolved(event(MaterialReportCaseStatus.DISMISSED, null,
                Set.of(firstReporterId)));

        verify(notificationService, never()).notifyUser(
                eq(tutorId), any(), any(), anyString(), anyString(), any(), anyString());
        verify(notificationService).notifyUser(
                eq(firstReporterId), eq(Role.STUDENT), any(), anyString(), anyString(), eq(caseId), anyString());
    }

    private MaterialReportResolvedEvent event(MaterialReportCaseStatus resolution, String removalReason,
            Set<UUID> reporters) {
        return new MaterialReportResolvedEvent(
                caseId,
                resolution,
                tutorId,
                "material.pdf",
                removalReason,
                reporters);
    }
}