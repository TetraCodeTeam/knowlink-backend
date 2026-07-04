package com.knowlink.api.tutors.data.dto.responses;

import java.util.List;
import java.util.UUID;

public record TutorProfileResponse(
        UUID id,
        String nombreCompleto,
        String biografia,
        String carrera,
        String fotoPerfil,
        boolean verificado,
        Double calificacionPromedio,
        List<TutorSubjectResponse> materias,
        List<TutorReviewResponse> calificaciones,
        List<TutorAvailabilityResponse> disponibilidad,
        List<TutorMaterialResponse> materiales
) {}
