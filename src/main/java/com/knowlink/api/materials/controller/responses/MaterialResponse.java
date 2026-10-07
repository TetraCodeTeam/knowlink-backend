package com.knowlink.api.materials.controller.responses;

import java.time.LocalDateTime;
import java.util.UUID;

public record MaterialResponse(
        UUID id,
        String name,
        String originalFileName,
        UUID subjectId,
        String subjectName,
        UUID tutorId,
        String tutorName,
        String format,
        String downloadUrl,
        LocalDateTime uploadedAt,
        Long sizeInBytes,
        int reportsCount,
        boolean alreadyReported
) {}
