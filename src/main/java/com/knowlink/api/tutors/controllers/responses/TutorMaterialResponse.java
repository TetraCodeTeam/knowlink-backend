package com.knowlink.api.tutors.controllers.responses;

import java.time.LocalDateTime;

public record TutorMaterialResponse(
        String name,
        String fileUrl,
        LocalDateTime uploadedAt
) {}