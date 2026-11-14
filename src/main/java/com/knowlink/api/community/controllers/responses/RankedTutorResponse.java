package com.knowlink.api.community.controllers.responses;

import com.knowlink.api.community.data.enums.PodiumBadge;

import java.util.List;
import java.util.UUID;

/**
 * Tutor del ranking mensual. tutorId es el id del usuario con rol TUTOR; badge solo existe en el podio.
 * score es el puntaje con el que se ordenó el ranking y bestAnswers / helpfulMarks son sus dos componentes de
 * comunidad (respuestas destacadas y marcas "Esto me sirvió" del mes anterior); el tercero es averageRating.
 */
public record RankedTutorResponse(
        UUID tutorId,
        int position,
        String fullName,
        String careerName,
        String profilePictureUrl,
        Double averageRating,
        int reviewsCount,
        int completedClasses,
        double score,
        int bestAnswers,
        int helpfulMarks,
        boolean verified,
        PodiumBadge badge,
        List<String> subjects
) {
}
