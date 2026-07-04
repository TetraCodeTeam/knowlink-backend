package com.knowlink.api.tutors.data.dto.responses;

import java.time.LocalDateTime;

public record TutorReviewResponse(
        Integer puntuacion,
        String comentario,
        LocalDateTime fechaCalificacion
) {}
