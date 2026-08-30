package com.knowlink.api.bookings.controllers.responses;

import java.time.Instant;

public record BookingSlotUnavailableWindowResponse(
        Instant start,
        Instant end,
        String status // "BLOCKED" | "RESERVED"
) {}
