package com.knowlink.api.tutors.support.controllers.responses;

import com.knowlink.api.claims.data.enums.ClaimReason;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.security.enums.Role;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record TutorSupportClaimResponse(
        UUID claimId,
        UUID bookingId,
        String subjectName,
        String studentName,
        LocalDate sessionDate,
        LocalTime sessionStartTime,
        LocalTime sessionEndTime,
        ClaimReason reason,
        Role claimantRole,
        ClaimStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
