package com.knowlink.api.admin.claims.controllers.responses;

import java.math.BigDecimal;

public record SessionClaimDetailResponse(
        SessionClaimListItemResponse summary,
        BigDecimal heldAmount,
        AutomaticOutcomeResponse automaticOutcome,
        ClaimPartyStatementResponse tutor,
        ClaimPartyStatementResponse student,
        ClaimPermissionsResponse permissions) {
}