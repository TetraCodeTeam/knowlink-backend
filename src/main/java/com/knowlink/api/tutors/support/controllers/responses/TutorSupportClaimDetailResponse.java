package com.knowlink.api.tutors.support.controllers.responses;

import com.knowlink.api.claims.controllers.responses.ClaimAttachmentUrlResponse;
import com.knowlink.api.claims.data.enums.ClaimReason;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.security.enums.Role;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record TutorSupportClaimDetailResponse(
        UUID claimId,
        UUID bookingId,
        StudentSummary student,
        String subjectName,
        String careerName,
        LocalDate sessionDate,
        LocalTime sessionStartTime,
        LocalTime sessionEndTime,
        ClaimReason reason,
        ClaimStatus status,
        Role claimantRole,
        String claimantName,
        String declaration,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        FundsSummary funds,
        ReviewProgress reviewProgress,
        List<ClaimAttachmentUrlResponse> attachments,
        List<ClaimCommentResponse> comments) {

    public record StudentSummary(String fullName, String profilePictureUrl, String careerName) {}

    public record FundsSummary(
            BigDecimal originalAmount,
            BigDecimal protectedAmount,
            BigDecimal protectedPercentage,
            FundsStatus status,
            String currency) {}

    public record ReviewProgress(
            String currentPhase,
            Integer queuePosition,
            LocalDateTime responseDueAt,
            long remainingMinutes) {}
}
