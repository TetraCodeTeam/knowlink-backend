package com.knowlink.api.ratings.controllers.interfaces;

import com.knowlink.api.ratings.controllers.responses.PendingRatingsResponse;
import com.knowlink.api.security.models.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/ratings")
@Tag(name = "Calificaciones", description = "Gestión de calificaciones de clases. Mientras el usuario tenga calificaciones pendientes, el resto de la API responde 423 RATING_REQUIRED.")
public interface IRatingGateController {

    @GetMapping("/pending")
    @Operation(summary = "Calificaciones pendientes que bloquean al usuario",
            description = "Devuelve las sesiones confirmadas y finalizadas que el usuario todavía no calificó, "
                    + "ordenadas de la más antigua a la más reciente, y el próximo fin de sesión que lo bloqueará.")
    @ApiResponse(responseCode = "200", description = "Calificaciones pendientes obtenidas")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    PendingRatingsResponse getPending(@AuthenticationPrincipal UserPrincipal principal);
}
