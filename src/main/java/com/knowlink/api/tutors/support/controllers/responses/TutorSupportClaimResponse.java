package com.knowlink.api.tutors.support.controllers.responses;

import com.knowlink.api.claims.data.enums.ClaimReason;
import com.knowlink.api.claims.data.enums.ClaimStatus;

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
        ClaimStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
