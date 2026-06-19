package com.knowlink.api.tutors.data.dto.responses;

import java.time.LocalTime;

public record TutorAvailabilityResponse(
        String dia,
        LocalTime horaInicio,
        LocalTime horaFin
) {}
