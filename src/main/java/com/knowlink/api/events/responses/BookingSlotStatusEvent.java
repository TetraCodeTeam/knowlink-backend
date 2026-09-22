package com.knowlink.api.events.responses;

import java.time.Instant;

public record BookingSlotStatusEvent(
        String slotId,
        String status,
        Instant windowStart,
        Instant windowEnd
) {}
