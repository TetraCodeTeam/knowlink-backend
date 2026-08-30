package com.knowlink.api.bookings.controllers.responses;

import java.time.LocalDateTime;
import java.util.UUID;

public record HoldResponse(
        UUID holdId,
        UUID slotId,
        String status,
        LocalDateTime expiresAt
) {}