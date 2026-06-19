package com.knowlink.api.tutors.data.dto.responses;

import java.time.LocalDateTime;

public record TutorMaterialResponse(
        String nombre,
        String urlArchivo,
        LocalDateTime fechaSubida
) {}
