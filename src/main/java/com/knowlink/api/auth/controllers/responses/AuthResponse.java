package com.knowlink.api.auth.controllers.responses;

public record AuthResponse(
        String token,
        String email,
        String firstName,
        String lastName,
        String role
) {}
