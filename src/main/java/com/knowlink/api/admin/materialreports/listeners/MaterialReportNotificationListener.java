package com.knowlink.api.admin.materialreports.listeners;

import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.events.MaterialReportResolvedEvent;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.users.services.interfaces.IEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MaterialReportNotificationListener {

    private final INotificationService notificationService;
    private final IEmailService emailService;
    private final IUserRepository userRepository;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMaterialReportResolved(MaterialReportResolvedEvent event) {
        try {
            userRepository.findById(event.tutorUserId())
                    .ifPresentOrElse(
                            tutor -> notifyTutor(tutor, event),
                            () -> log.warn("Tutor {} not found for resolved material report case {}",
                                    event.tutorUserId(), event.caseId()));

            Set<UUID> reporterUserIds = event.reporterUserIds() == null
                    ? Set.of()
                    : event.reporterUserIds();

            reporterUserIds.stream()
                    .filter(reporterId -> !reporterId.equals(event.tutorUserId()))
                    .distinct()
                    .forEach(reporterId -> userRepository.findById(reporterId)
                            .ifPresentOrElse(
                                    reporter -> notifyReporter(reporter, event),
                                    () -> log.warn("Reporter {} not found for resolved material report case {}",
                                            reporterId, event.caseId())));
        } catch (Exception exception) {
            log.error("Failed to process material report notifications for case {}: {}",
                    event.caseId(), exception.getMessage(), exception);
        }
    }

    private void notifyTutor(User tutor, MaterialReportResolvedEvent event) {
        boolean removed = event.resolution() == MaterialReportCaseStatus.REMOVED;
        String title = removed ? "Material retirado" : "Material revisado";
        String message = removed
                ? "Tu material «" + event.materialName() + "» fue retirado por incumplimiento de las normas."
                : "Tu material «" + event.materialName() + "» fue revisado y se mantiene disponible.";

        if (removed && hasText(event.removalReason())) {
            message = message + " Motivo: " + event.removalReason();
        }

        notifyInAppQuietly(tutor.getUserId(), Role.TUTOR, title, message, event);
        sendEmailQuietly(tutor, title, title, message, event);
    }

    private void notifyReporter(User reporter, MaterialReportResolvedEvent event) {
        boolean removed = event.resolution() == MaterialReportCaseStatus.REMOVED;
        String title = "Resultado de tu reporte";
        String message = removed
                ? "Procesamos tu reporte sobre «" + event.materialName() + "»: el material fue retirado."
                : "Procesamos tu reporte sobre «" + event.materialName() + "»: no encontramos infracciones.";

        notifyInAppQuietly(reporter.getUserId(), Role.STUDENT, title, message, event);
        sendEmailQuietly(reporter, title, title, message, event);
    }

    private void notifyInAppQuietly(UUID recipientUserId, Role recipientRole, String title, String message,
            MaterialReportResolvedEvent event) {
        String dedupeKey = "material-report-resolved:" + event.caseId() + ":" + recipientUserId;
        try {
            notificationService.notifyUser(recipientUserId, recipientRole,
                    NotificationType.MATERIAL_REPORT_PROCESSED, title, message, event.caseId(), dedupeKey);
        } catch (Exception exception) {
            log.error("Failed to create in-app notification for user {} on case {}: {}",
                    recipientUserId, event.caseId(), exception.getMessage(), exception);
        }
    }

    private void sendEmailQuietly(User recipient, String subject, String heading, String bodyText,
            MaterialReportResolvedEvent event) {
        try {
            emailService.sendAdminNotificationEmail(recipient, subject, heading, bodyText);
        } catch (Exception exception) {
            log.error("Failed to send email to user {} for material report case {}: {}",
                    recipient.getUserId(), event.caseId(), exception.getMessage(), exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}