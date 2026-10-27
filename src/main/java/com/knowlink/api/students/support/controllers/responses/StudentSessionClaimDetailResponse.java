package com.knowlink.api.students.support.controllers.responses;

import com.knowlink.api.claims.controllers.responses.ClaimAttachmentUrlResponse;
import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.claims.controllers.responses.ClaimFundsSummaryResponse;
import com.knowlink.api.claims.controllers.responses.ClaimReviewProgressResponse;
import com.knowlink.api.claims.data.enums.ClaimReason;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.security.enums.Role;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record StudentSessionClaimDetailResponse(
        UUID claimId,
        UUID bookingId,
        TutorSummary tutor,
        String subjectName,
        String careerName,
        LocalDate sessionDate,
        LocalTime sessionStartTime,
        LocalTime sessionEndTime,
        ClaimReason reason,
        String reasonLabel,
        ClaimStatus status,
        String statusLabel,
        Role claimantRole,
        boolean initiatedByMe,
        String claimantName,
        String declaration,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        ClaimFundsSummaryResponse funds,
        ClaimReviewProgressResponse reviewProgress,
        List<ClaimAttachmentUrlResponse> attachments,
        List<ClaimCommentResponse> comments) {

    public record TutorSummary(String fullName, String profilePictureUrl) {}
}
