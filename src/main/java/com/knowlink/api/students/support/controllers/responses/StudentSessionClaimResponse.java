package com.knowlink.api.students.support.controllers.responses;

import com.knowlink.api.claims.data.enums.ClaimReason;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.security.enums.Role;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record StudentSessionClaimResponse(
        UUID claimId,
        UUID bookingId,
        String tutorName,
        String tutorProfilePictureUrl,
        String subjectName,
        LocalDate sessionDate,
        LocalTime sessionStartTime,
        LocalTime sessionEndTime,
        ClaimReason reason,
        String reasonLabel,
        Role claimantRole,
        boolean initiatedByMe,
        ClaimStatus status,
        String statusLabel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
