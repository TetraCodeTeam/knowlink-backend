package com.knowlink.api.claims.controllers.responses;

import java.util.UUID;

public record ClaimAttachmentResponse(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes) {}