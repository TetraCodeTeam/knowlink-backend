package com.knowlink.api.claims.controllers.responses;

import java.time.LocalDateTime;

public record ClaimReviewProgressResponse(
        String currentPhase,
        Integer queuePosition,
        LocalDateTime responseDueAt,
        long remainingMinutes) {}
