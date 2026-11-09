package com.knowlink.api.tutors.data.projections;

import java.util.UUID;

/**
 * Per-subject aggregates behind the derived verification flag: the exact sum
 * and count of the ratings that count for one TutorSubject. The sum/count pair
 * is used instead of AVG so the average can be divided with full precision
 * before the threshold comparison (no floating point rounding at the 4.5 edge).
 */
public interface SubjectVerificationAggregate {

    UUID getTutorSubjectId();

    Long getTotalScore();

    Long getRatingCount();
}