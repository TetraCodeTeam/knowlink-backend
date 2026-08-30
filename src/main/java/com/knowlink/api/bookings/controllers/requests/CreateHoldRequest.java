package com.knowlink.api.bookings.controllers.requests;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateHoldRequest(
        @NotNull(message = "slotId is required")
        UUID slotId,

        @NotNull(message = "start is required")
        Instant start,

        @NotNull(message = "end is required")
        Instant end
) {}
