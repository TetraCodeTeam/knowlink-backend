package com.knowlink.api.tutors.services.implementations;

import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.tutors.config.SubjectVerificationProperties;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.data.projections.SubjectVerificationAggregate;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.tutors.services.interfaces.ISubjectVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Recalculates the derived verification of every subject of a tutor from the
 * ratings that count (visible, COMPLETED booking, rated = tutor, grouped by
 * TutorSubject) and keeps the derived tutor badge in sync.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubjectVerificationServiceImpl implements ISubjectVerificationService {

    private static final String GAINED_TITLE = "Materia verificada";
    private static final String LOST_TITLE = "Materia sin verificar";
    private static final String NOTIFICATION_TYPE_KEY = "SUBJECT_VALIDATION_UPDATED";
    private static final int STORED_AVERAGE_SCALE = 2;

    private final ITutorSubjectRepository tutorSubjectRepository;
    private final ITutorProfileRepository tutorProfileRepository;
    private final IRatingRepository ratingRepository;
    private final SubjectVerificationProperties properties;
    private final INotificationService notificationService;

    @Override
    @Transactional
    public void recalculateForTutor(UUID tutorUserId) {
        TutorProfile tutorProfile = tutorProfileRepository.findByUserId(tutorUserId).orElse(null);
        if (tutorProfile == null) {
            log.debug("No tutor profile for user {}: nothing to verify", tutorUserId);
            return;
        }

        List<TutorSubject> subjects =
                tutorSubjectRepository.findByTutorProfileId(tutorProfile.getTutorProfileId());
        if (subjects.isEmpty()) {
            return;
        }

        Map<UUID, SubjectVerificationAggregate> aggregates = ratingRepository
                .findSubjectAggregatesByTutorId(tutorUserId)
                .stream()
                .collect(Collectors.toMap(
                        SubjectVerificationAggregate::getTutorSubjectId, aggregate -> aggregate));

        List<TutorSubject> changed = new ArrayList<>();
        List<VerificationTransition> transitions = new ArrayList<>();

        for (TutorSubject subject : subjects) {
            SubjectVerificationAggregate aggregate = aggregates.get(subject.getTutorSubjectId());
            int count = ratingCountOf(aggregate);
            BigDecimal unroundedAverage = averageOf(aggregate, count);
            BigDecimal storedAverage = truncate(unroundedAverage);
            boolean verified = isVerified(unroundedAverage, count);

            boolean countChanged = subject.getRatingCount() != count;
            boolean averageChanged = !sameAverage(storedAverage, subject.getAverageRating());
            boolean verifiedChanged = subject.isVerified() != verified;
            if (!countChanged && !averageChanged && !verifiedChanged) {
                continue;
            }

            if (verifiedChanged) {
                log.info("Subject {} verification changed {} -> {} (average={}, ratings={})",
                        subject.getTutorSubjectId(), subject.isVerified(), verified,
                        unroundedAverage, count);
                transitions.add(new VerificationTransition(subject, verified));
            }

            subject.setRatingCount(count);
            subject.setAverageRating(storedAverage);
            subject.setVerified(verified);
            changed.add(subject);
        }

        if (!changed.isEmpty()) {
            tutorSubjectRepository.saveAll(changed);
        }
        // The badge is refreshed even when no subject changed: deactivating the
        // last verified subject has to clear it on the next recalculation.
        refreshTutorBadge(tutorUserId, tutorProfile, subjects);
        transitions.forEach(transition -> notifyTransition(tutorUserId, transition));
    }

    /**
     * The tutor badge is derived: a tutor is verified when at least one of the
     * subjects is verified and visible in the catalogue.
     */
    private void refreshTutorBadge(UUID tutorUserId, TutorProfile tutorProfile, List<TutorSubject> subjects) {
        boolean verifiedBadge = subjects.stream()
                .anyMatch(subject -> subject.isVerified() && subject.isActive());
        if (tutorProfile.isVerified() == verifiedBadge) {
            return;
        }
        tutorProfile.setVerified(verifiedBadge);
        tutorProfileRepository.save(tutorProfile);
        log.info("Tutor {} verified badge changed to {}", tutorUserId, verifiedBadge);
    }

    private boolean isVerified(BigDecimal unroundedAverage, int count) {
        if (unroundedAverage == null || count < properties.minRatings()) {
            return false;
        }
        // Compared without rounding so 4.49 never verifies just because it
        // displays as 4.5 (and 4.5 always does, even if stored as 4.50).
        return unroundedAverage.compareTo(properties.minAverage()) >= 0
                && unroundedAverage.compareTo(properties.maxAverage()) <= 0;
    }

    private int ratingCountOf(SubjectVerificationAggregate aggregate) {
        if (aggregate == null || aggregate.getRatingCount() == null) {
            return 0;
        }
        return Math.toIntExact(aggregate.getRatingCount());
    }

    private BigDecimal averageOf(SubjectVerificationAggregate aggregate, int count) {
        if (aggregate == null || count <= 0 || aggregate.getTotalScore() == null) {
            return null;
        }
        return BigDecimal.valueOf(aggregate.getTotalScore())
                .divide(BigDecimal.valueOf(count), MathContext.DECIMAL64);
    }

    /** Stored truncated, so the persisted value never overstates the real average. */
    private BigDecimal truncate(BigDecimal average) {
        return average == null ? null : average.setScale(STORED_AVERAGE_SCALE, RoundingMode.DOWN);
    }

    private static boolean sameAverage(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private void notifyTransition(UUID tutorUserId, VerificationTransition transition) {
        TutorSubject subject = transition.subject();
        String subjectName = subject.getSubject().getName();
        String title = transition.verified() ? GAINED_TITLE : LOST_TITLE;
        String message = transition.verified()
                ? "Tu materia " + subjectName
                        + " alcanzó el promedio mínimo de calificaciones y ahora está verificada."
                : "Tu materia " + subjectName
                        + " bajó del promedio mínimo de calificaciones y dejó de estar verificada.";
        // Key includes the resulting state so a later opposite transition can
        // notify again, while repeated recalculations of the same state do not.
        String dedupeKey = NOTIFICATION_TYPE_KEY + ":" + subject.getTutorSubjectId()
                + ":" + (transition.verified() ? "VERIFIED" : "UNVERIFIED");
        try {
            notificationService.notifyUser(tutorUserId, Role.TUTOR,
                    NotificationType.SUBJECT_VALIDATION_UPDATED, title, message,
                    subject.getTutorSubjectId(), dedupeKey);
        } catch (RuntimeException ex) {
            // Verification must not fail because the notification could not be sent.
            log.error("Could not notify subject verification change for subject {}",
                    subject.getTutorSubjectId(), ex);
        }
    }

    private record VerificationTransition(TutorSubject subject, boolean verified) {
    }
}