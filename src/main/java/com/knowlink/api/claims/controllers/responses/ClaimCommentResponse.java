package com.knowlink.api.claims.controllers.responses;

import com.knowlink.api.security.enums.Role;

import java.time.LocalDateTime;
import java.util.UUID;

public record ClaimCommentResponse(
        UUID id,
        String authorName,
        Role authorRole,
        String comment,
        LocalDateTime createdAt) {}
