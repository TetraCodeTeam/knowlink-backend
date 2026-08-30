package com.knowlink.api.bookings.controllers.responses;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingSlotResponse(
        UUID id,
        Instant start,
        Instant end,
        String status, // "AVAILABLE" | "BLOCKED" | "RESERVED"
        List<BookingSlotUnavailableWindowResponse> unavailableWindows
) {}
