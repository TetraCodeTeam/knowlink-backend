package com.knowlink.api.users.controllers.responses;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID userId,
        String email,
        String role,
        String accountStatus,
        String profilePictureUrl,
        LocalDateTime createdAt
) {}