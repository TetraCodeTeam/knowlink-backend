package com.knowlink.api.community.controllers.responses;

import java.time.LocalDate;
import java.util.List;

/** Ranking mensual: los 3 primeros puestos como podio y el resto del top 10 como tabla. */
public record TutorPodiumResponse(
        LocalDate periodStart,
        List<RankedTutorResponse> podium,
        List<RankedTutorResponse> ranking
) {
}
