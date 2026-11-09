package com.knowlink.api.tutors.services.interfaces;

import java.util.UUID;

/**
 * Keeps the derived verification of a tutor's subjects in sync with the
 * ratings that count (visible, COMPLETED booking, rated = tutor, grouped by
 * TutorSubject). Verification is never set by hand: creating or activating a
 * subject does not grant it.
 */
public interface ISubjectVerificationService {

    /**
     * Recalculates every subject of the tutor from scratch (idempotent, it
     * re-reads the aggregates instead of incrementing them) and updates the
     * derived tutor badge. Last write wins, no locking required.
     */
    void recalculateForTutor(UUID tutorUserId);
}