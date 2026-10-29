package com.knowlink.api.admin.claims.controllers.responses;

import com.knowlink.api.admin.claims.data.enums.ClaimFavoredParty;
import com.knowlink.api.claims.data.enums.ClaimStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SessionClaimListItemResponse(
        UUID claimId,
        String subjectName,
        String tutorName,
        String studentName,
        LocalDate sessionDate,
        ClaimStatus status,
        LocalDateTime openedAt,
        AssignedAdminResponse assignedAdmin,
        boolean assignedToMe,
        LocalDateTime resolvedAt,
        ClaimFavoredParty favoredParty,
        String resolutionJustification) {
}