package com.knowlink.api.resources.dto;

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
        Long sizeInBytes
) {}
