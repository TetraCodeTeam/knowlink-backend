package com.knowlink.api.tutors.controllers.responses;

import java.util.List;
import java.util.UUID;

public record TutorSelfProfileResponse(
        UUID userId,
        String fullName,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String dni,
        UUID careerId,
        String career,
        UUID institutionId,
        String institution,
        String institutionalId,
        String profilePictureUrl,
        String biography,
        String address,
        boolean mercadoPagoLinked,
        Double averageRating,
        boolean hasStudentProfile,
        List<TutorSubjectResponse> subjects
) {}
