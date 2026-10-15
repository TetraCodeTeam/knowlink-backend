package com.knowlink.api.claims.controllers.requests;

import com.knowlink.api.claims.data.enums.ClaimReason;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateClaimRequest(
        @NotNull(message = "reason is required")
        ClaimReason reason,
        @Size(max = 500, message = "comment must be at most 500 characters")
        String comment) {}