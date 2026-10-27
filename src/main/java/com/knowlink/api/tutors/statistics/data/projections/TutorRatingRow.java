package com.knowlink.api.tutors.statistics.data.projections;

import java.util.UUID;

public record TutorRatingRow(int score, UUID tutorSubjectId) {
}
