package com.knowlink.api.claims.controllers.responses;

import java.util.UUID;

public record ClaimAttachmentUrlResponse(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes,
        String signedUrl) {}