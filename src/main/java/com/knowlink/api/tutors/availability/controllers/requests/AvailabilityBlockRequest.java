package com.knowlink.api.tutors.availability.controllers.requests;

import com.knowlink.api.shared.utils.EndOfDay;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record AvailabilityBlockRequest(
        @NotNull(message = "Date is required")
        LocalDate date,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "End time is required")
        LocalTime endTime,

        @NotNull(message = "repeatWeekly is required")
        Boolean repeatWeekly
) {

    // Un bloque que termina a las 00:00 llega como medianoche del día siguiente (fin de día).
    public AvailabilityBlockRequest {
        endTime = EndOfDay.normalizeEnd(endTime);
    }
}