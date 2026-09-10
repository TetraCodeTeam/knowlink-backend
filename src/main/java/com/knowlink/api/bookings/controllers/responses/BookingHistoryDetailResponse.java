package com.knowlink.api.bookings.controllers.responses;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.tutors.data.enums.Modality;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record BookingHistoryDetailResponse(
        UUID bookingId,
        String otherPartyFullName,
        String otherPartyProfilePictureUrl,
        String subjectName,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        Modality modality,
        BookingStatus status,
        BigDecimal amount,
        String topic,
        String virtualSessionLink, // solo si modality == VIRTUAL
        String address,            // solo si modality == IN_PERSON
        LocalDateTime createdAt
) {}