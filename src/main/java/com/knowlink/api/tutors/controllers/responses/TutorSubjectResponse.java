package com.knowlink.api.tutors.controllers.responses;

import java.math.BigDecimal;
import java.util.UUID;

public record TutorSubjectResponse(
        UUID tutorSubjectId,
        String subjectName,
        String modality,
        String compensationType,
        BigDecimal pricePerHour,
        String verificationStatus, // "PENDING" | "ACTIVE" | "REJECTED"
        Double averageRating,      // null por ahora — pendiente de US de calificaciones por materia
        Integer reviewCount        // null por ahora, mismo motivo
) {}