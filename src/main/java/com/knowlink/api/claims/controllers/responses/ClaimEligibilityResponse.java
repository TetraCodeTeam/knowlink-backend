package com.knowlink.api.claims.controllers.responses;

import com.knowlink.api.claims.data.enums.ClaimBlockReason;
import com.knowlink.api.claims.data.enums.ClaimReason;

import java.time.LocalDateTime;
import java.util.List;

public record ClaimEligibilityResponse(
        boolean canClaim,
        LocalDateTime claimableUntil,
        ClaimBlockReason blockReason,
        List<ClaimReason> allowedReasons) {}
