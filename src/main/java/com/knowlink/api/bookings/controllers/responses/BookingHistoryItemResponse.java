package com.knowlink.api.bookings.controllers.responses;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.tutors.data.enums.Modality;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record BookingHistoryItemResponse(
        UUID bookingId,
        String otherPartyFullName,
        String subjectName,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        Modality modality,
        BookingStatus status
) {}