package com.knowlink.api.tutors.controllers.responses;

import java.util.List;
import java.util.UUID;

import com.knowlink.api.tutors.availability.controllers.responses.AvailabilityBlockResponse;

public record TutorProfileResponse(
        UUID id,
        String fullName,
        String biography,
        String career,
        String profilePictureUrl,
        String address,
        boolean verified,
        Double averageRating,
        List<TutorSubjectResponse> subjects,
        List<TutorReviewResponse> reviews,
        List<AvailabilityBlockResponse> availability,
        List<TutorMaterialResponse> materials
) {}