package com.knowlink.api.tutors.controllers.responses;

import java.math.BigDecimal;
import java.util.UUID;

public record TutorSubjectResponse(
        UUID tutorSubjectId,
        String subjectName,
        String modality,
        String compensationType,
        BigDecimal pricePerHour,
        String verificationStatus, // derived: verified ? "ACTIVE" : "PENDING"
        Double averageRating,      // average of the ratings that count for the subject
        Integer reviewCount,       // how many ratings were counted
        boolean active,            // US-12: subject offered (true) or deactivated (false)
        boolean verified           // derived from the rating average (never set by hand)
) {}