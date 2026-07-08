package com.knowlink.api.tutors.controllers.responses;

import java.util.List;
import java.util.UUID;

public record TutorProfileResponse(
        UUID id,
        String fullName,
        String biography,
        String career,
        String profilePictureUrl,
        boolean verified,
        Double averageRating,
        List<TutorSubjectResponse> subjects,
        List<TutorReviewResponse> reviews,
        List<TutorAvailabilityResponse> availability,
        List<TutorMaterialResponse> materials
) {}