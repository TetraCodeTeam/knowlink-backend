package com.knowlink.api.bookings.controllers.responses;

import com.knowlink.api.bookings.data.enums.BookingStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record BookingConfirmationResponse(
        UUID bookingId,
        BookingStatus status,
        LocalDateTime confirmedAt
) {}