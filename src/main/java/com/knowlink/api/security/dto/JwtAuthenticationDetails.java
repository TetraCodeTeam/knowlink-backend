package com.knowlink.api.security.dto;

import java.util.UUID;

public record JwtAuthenticationDetails(
        UUID userId,
        String email,
        String role
) {}
