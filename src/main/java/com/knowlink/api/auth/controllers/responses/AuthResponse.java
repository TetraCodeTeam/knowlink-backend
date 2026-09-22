package com.knowlink.api.auth.controllers.responses;

import com.knowlink.api.security.enums.Role;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String email,
        String token,
        Role role
) {}