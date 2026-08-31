package com.knowlink.api.bookings.controllers.responses;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.tutors.data.enums.Modality;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record BookingResponse(
        UUID bookingId,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal amount,
        Modality modality,
        String topic,
        BookingStatus bookingStatus,
        String tutorSubjectName,
        String tutorFullName,
        String studentFullName
) {}