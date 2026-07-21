package com.knowlink.api.tutors.data.models;

import java.util.List;
import java.util.UUID;

public record TutorSearchResponse(
        UUID tutorId,
        String fullName,
        String photoProfile,
        Double averageRating,
        Integer totalReviews,
        List<SubjectSummary> subjects
) {
}