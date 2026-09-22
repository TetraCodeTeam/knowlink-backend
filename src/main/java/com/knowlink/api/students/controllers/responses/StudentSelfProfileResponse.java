package com.knowlink.api.students.controllers.responses;

import com.knowlink.api.security.enums.Role;

import java.util.UUID;

public record StudentSelfProfileResponse(
        UUID userId,
        String fullName,
        String email,
        String phoneNumber,
        String career,
        String profilePictureUrl,
        Role role,
        boolean hasTutorProfile
) {}
