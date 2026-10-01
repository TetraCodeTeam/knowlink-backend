package com.knowlink.api.claims.controllers.responses;

import com.knowlink.api.claims.data.enums.ClaimBlockReason;

import java.time.LocalDateTime;

public record ClaimEligibilityResponse(
        boolean canClaim,
        LocalDateTime claimableUntil,
        ClaimBlockReason blockReason) {}