package com.knowlink.api.bookings.controllers.requests;

import com.knowlink.api.tutors.data.enums.Modality;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateBookingRequest(
        @NotBlank(message = "bookingSlotId is required")
        String bookingSlotId,

        @NotNull(message = "tutorSubjectId is required")
        UUID tutorSubjectId, 

        @NotBlank(message = "topic is required")
        String topic,

        @NotNull(message = "modality is required")
        Modality modality,

        @NotNull(message = "start is required")
        Instant start,

        @NotNull(message = "end is required")
        Instant end
) {}