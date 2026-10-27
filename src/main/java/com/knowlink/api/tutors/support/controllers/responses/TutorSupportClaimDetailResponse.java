package com.knowlink.api.tutors.support.controllers.responses;

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
        ClaimFundsSummaryResponse funds,
        ClaimReviewProgressResponse reviewProgress,
        List<ClaimAttachmentUrlResponse> attachments,
        List<ClaimCommentResponse> comments) {

    public record StudentSummary(String fullName, String profilePictureUrl, String careerName) {}
}
