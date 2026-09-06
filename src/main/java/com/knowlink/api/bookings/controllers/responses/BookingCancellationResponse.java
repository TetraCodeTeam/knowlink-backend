package com.knowlink.api.bookings.controllers.responses;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.enums.RefundDestination;
import com.knowlink.api.bookings.data.enums.RefundPolicy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record BookingCancellationResponse(
        UUID bookingId,
        BookingStatus bookingStatus,
        UUID cancellationId,
        RefundDestination refundDestination,
        RefundPolicy refundPolicy,
        BigDecimal amount,
        Long hoursInAdvance,
        LocalDateTime createdAt
) {}
