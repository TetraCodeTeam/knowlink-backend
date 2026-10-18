package com.knowlink.api.tutors.support.controllers.requests;

import com.knowlink.api.claims.data.enums.ClaimStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateClaimStatusRequest(@NotNull ClaimStatus status) {
}
