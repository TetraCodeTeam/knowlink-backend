package com.knowlink.api.ratings.controllers.responses;

import com.knowlink.api.security.enums.Role;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record PendingRatingItemResponse(
        UUID bookingId,
        Role raterRole,
        String subjectName,
        String otherPartyFullName,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime) {
}
