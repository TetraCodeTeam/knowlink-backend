package com.knowlink.api.bookings.controllers.responses;

import java.time.Instant;
import java.util.UUID;

public record ActiveHoldResponse(
        UUID holdId,
        UUID tutorUserId,
        String tutorFullName,
        UUID timeSlotId,
        Instant start,
        Instant end,
        Instant expiresAt
) {}