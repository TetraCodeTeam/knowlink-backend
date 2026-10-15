package com.knowlink.api.tutors.controllers.responses;

import java.util.UUID;

public record RatingAuthorResponse(
        UUID userId,
        String fullName,
        String profilePictureUrl) {
}