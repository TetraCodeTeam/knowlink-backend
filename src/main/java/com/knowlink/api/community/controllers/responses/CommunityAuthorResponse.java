package com.knowlink.api.community.controllers.responses;

import com.knowlink.api.security.enums.Role;

import java.util.UUID;

/**
 * Autor de una publicación o respuesta. Cuando el rol es TUTOR, userId es también el tutorId que usan
 * los endpoints de perfil y reserva.
 */
public record CommunityAuthorResponse(
        UUID userId,
        String fullName,
        Role role,
        String profilePictureUrl,
        String careerName,
        Double averageRating,
        boolean verified,
        boolean canBookClass
) {
}
