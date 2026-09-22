package com.knowlink.api.bookings.controllers.responses;

import java.time.Instant;
import java.util.UUID;

public record HoldResponse(
        UUID holdId,
        UUID slotId,
        String status,
        Instant expiresAt
) {}