package com.knowlink.api.tutors.controllers.responses;

import java.time.LocalDateTime;
import java.util.UUID;

public record RatingCommentResponse(
        UUID id,
        UUID subjectId,
        String subjectName,
        Integer score,
        String comment,
        LocalDateTime ratingDate) {
}