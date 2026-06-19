package com.knowlink.api.tutors.data.dto.responses;

public record TutorSubjectResponse(
        String materia,
        String descripcion,
        String modalidad,
        String tipoCompensacion,
        Double precio
) {}
