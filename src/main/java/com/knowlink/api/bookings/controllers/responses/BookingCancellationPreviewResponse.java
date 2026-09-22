package com.knowlink.api.bookings.controllers.responses;

import com.knowlink.api.bookings.data.enums.CancellationRole;
import com.knowlink.api.bookings.data.enums.RefundDestination;
import com.knowlink.api.bookings.data.enums.RefundPolicy;

import java.math.BigDecimal;
import java.util.UUID;

public record BookingCancellationPreviewResponse(
        UUID bookingId,
        CancellationRole cancellationRole,
        Long hoursInAdvance,
        RefundDestination refundDestination,
        BigDecimal amount,
        RefundPolicy refundPolicy
) {}
