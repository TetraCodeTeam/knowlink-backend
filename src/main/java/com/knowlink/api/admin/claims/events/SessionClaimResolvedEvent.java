package com.knowlink.api.admin.claims.events;

import com.knowlink.api.admin.claims.data.enums.ClaimFavoredParty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published inside the resolution transaction and consumed after commit to notify both parties.
 */
public record SessionClaimResolvedEvent(
        UUID claimId,
        UUID bookingId,
        UUID tutorUserId,
        UUID studentUserId,
        ClaimFavoredParty favoredParty,
        String justification,
        String subjectName,
        LocalDate sessionDate,
        BigDecimal movedAmount) {
}