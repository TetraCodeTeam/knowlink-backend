package com.knowlink.api.admin.claims.listeners;

import com.knowlink.api.admin.claims.data.enums.ClaimFavoredParty;
import com.knowlink.api.admin.claims.events.SessionClaimResolvedEvent;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.users.services.interfaces.IEmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Notifies both parties once the claim resolution is committed. Contact data of the counterparty is
 * never included; mail failures are logged and ignored because the resolution is already confirmed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionClaimNotificationListener {

    private static final String EMAIL_SUBJECT = "Reclamo de sesión resuelto — KnowLink";
    private static final String EMAIL_HEADING = "Tu reclamo fue resuelto";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Column length of Notification.message: the in-app text must always fit in it. */
    private static final int IN_APP_MESSAGE_MAX_LENGTH = 500;

    private final INotificationService notificationService;
    private final IEmailService emailService;
    private final IUserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSessionClaimResolved(SessionClaimResolvedEvent event) {
        notifyParty(event, event.tutorUserId(), Role.TUTOR);
        notifyParty(event, event.studentUserId(), Role.STUDENT);
    }

    private void notifyParty(SessionClaimResolvedEvent event, UUID userId, Role role) {
        if (userId == null) {
            return;
        }
        String inAppMessage = truncateToInAppLimit(buildInAppMessage(event));
        String emailMessage = buildEmailMessage(event);
        String dedupeKey = "session-claim-resolved:" + event.claimId() + ":" + userId;

        try {
            notificationService.notifyUser(
                    userId,
                    role,
                    NotificationType.SESSION_CLAIM_UPDATE,
                    "Reclamo resuelto",
                    inAppMessage,
                    event.claimId(),
                    dedupeKey);
        } catch (Exception ex) {
            log.error("Failed to create the in-app notification of claim {} for user {}: {}",
                    event.claimId(), userId, ex.getMessage(), ex);
        }

        try {
            User recipient = userRepository.findById(userId).orElse(null);
            if (recipient == null) {
                log.warn("Recipient {} of claim {} not found, email skipped", userId, event.claimId());
                return;
            }
            emailService.sendAdminNotificationEmail(recipient, EMAIL_SUBJECT, EMAIL_HEADING, emailMessage);
        } catch (Exception ex) {
            log.error("Failed to send the resolution email of claim {} to user {}: {}",
                    event.claimId(), userId, ex.getMessage(), ex);
        }
    }

    /**
     * In-app message: always short, it invites the user to open the detail where the complete
     * justification lives. Kept under the 500 chars of Notification.message as a safety net.
     */
    private String buildInAppMessage(SessionClaimResolvedEvent event) {
        return baseMessage(event) + fundsOutcome(event) + " Abrí el detalle para leer la justificación.";
    }

    /** Email message: keeps the complete resolution justification. */
    private String buildEmailMessage(SessionClaimResolvedEvent event) {
        return baseMessage(event) + "Justificación: " + event.justification() + ". " + fundsOutcome(event);
    }

    private String baseMessage(SessionClaimResolvedEvent event) {
        String favoredParty = event.favoredParty() == ClaimFavoredParty.TUTOR ? "del tutor" : "del alumno";
        return "El reclamo por la sesión de " + event.subjectName() + " del " + formatDate(event.sessionDate())
                + " se resolvió a favor " + favoredParty + ". ";
    }

    private String truncateToInAppLimit(String message) {
        return message.length() <= IN_APP_MESSAGE_MAX_LENGTH
                ? message
                : message.substring(0, IN_APP_MESSAGE_MAX_LENGTH);
    }

    private String fundsOutcome(SessionClaimResolvedEvent event) {
        BigDecimal movedAmount = event.movedAmount();
        if (movedAmount == null || movedAmount.compareTo(BigDecimal.ZERO) == 0) {
            return "No había fondos retenidos para esta sesión.";
        }
        return event.favoredParty() == ClaimFavoredParty.TUTOR
                ? "Los fondos retenidos fueron liberados al tutor."
                : "Los fondos retenidos fueron reintegrados al alumno.";
    }

    private String formatDate(LocalDate date) {
        return date == null ? "la fecha registrada" : DATE_FORMAT.format(date);
    }
}