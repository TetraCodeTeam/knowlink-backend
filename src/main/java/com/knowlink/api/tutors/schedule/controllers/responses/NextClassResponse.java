package com.knowlink.api.tutors.schedule.controllers.responses;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record NextClassResponse(
        UUID bookingId,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime
) {}
