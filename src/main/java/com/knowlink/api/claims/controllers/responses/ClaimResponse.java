package com.knowlink.api.claims.controllers.responses;

import com.knowlink.api.claims.data.enums.ClaimReason;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.security.enums.Role;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ClaimResponse(
        UUID id,
        UUID bookingId,
        ClaimReason reason,
        String comment,
        ClaimStatus status,
        Role claimantRole,
        LocalDateTime createdAt,
        List<ClaimAttachmentResponse> attachments) {}