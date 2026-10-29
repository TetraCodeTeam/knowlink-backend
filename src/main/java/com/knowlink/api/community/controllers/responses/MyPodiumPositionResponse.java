package com.knowlink.api.community.controllers.responses;

/**
 * Posición del tutor autenticado en el ranking vigente. eligible es false si el usuario no tiene un rol de
 * tutor activo; position es null si es elegible pero todavía no suma puntaje en el período.
 * positionsToTopTen es 0 cuando el tutor ya está dentro del top 10.
 */
public record MyPodiumPositionResponse(
        boolean eligible,
        Integer position,
        String fullName,
        String careerName,
        int positionsToTopTen,
        int progressPercent
) {
}
