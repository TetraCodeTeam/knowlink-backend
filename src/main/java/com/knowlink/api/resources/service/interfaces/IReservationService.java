package com.knowlink.api.resources.service.interfaces;

import java.util.UUID;

public interface IReservationService {
    boolean hasActiveReservation(UUID userId, UUID subjectId);

    /**
     * Checks if the student has an active reservation with ANY tutor for the given subject.
     * Used as initial gate in the materials listing endpoint.
     */
    boolean hasAnyReservationForSubject(UUID userId, UUID subjectId);

    /**
     * Checks if the student has an active reservation with a specific tutor for the given subject.
     * Used to filter materials per tutor and to validate download access.
     */
    boolean hasReservationWithTutorForSubject(UUID userId, UUID tutorId, UUID subjectId);
}
