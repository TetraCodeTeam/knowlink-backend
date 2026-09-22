package com.knowlink.api.tutors.controllers.responses;

import java.time.LocalDateTime;

public record TutorReviewResponse(
        Integer score,
        String comment,
        LocalDateTime ratingDate
) {}