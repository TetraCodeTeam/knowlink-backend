package com.knowlink.api.community.controllers.responses;

import com.knowlink.api.community.data.enums.PodiumBadge;

import java.util.List;
import java.util.UUID;

/** Tutor del ranking mensual. tutorId es el id del usuario con rol TUTOR; badge solo existe en el podio. */
public record RankedTutorResponse(
        UUID tutorId,
        int position,
        String fullName,
        String careerName,
        String profilePictureUrl,
        Double averageRating,
        int reviewsCount,
        int completedClasses,
        boolean verified,
        PodiumBadge badge,
        List<String> subjects
) {
}
