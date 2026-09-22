package com.knowlink.api.tutors.availability.controllers.requests;

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
) {}