package com.knowlink.api.tutors.controllers.responses;

import java.util.List;
import java.util.UUID;

public record TutorRatingHistoryResponse(
        UUID tutorId,
        Double averageRating,
        Long totalRatings,
        List<SubjectAverageResponse> subjects,
        PagedCommentsResponse comments) {
}