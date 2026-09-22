package com.knowlink.api.bookings.events;

import com.knowlink.api.bookings.data.enums.RefundDestination;

import java.math.BigDecimal;
import java.util.UUID;

public record BookingCancelledEvent(
        UUID bookingId,
        UUID timeSlotId,
        UUID tutorProfileId,
        RefundDestination refundDestination,
        BigDecimal amount
) {}
