package com.knowlink.api.community.controllers.interfaces;

import com.knowlink.api.community.controllers.responses.MyPodiumPositionResponse;
import com.knowlink.api.community.controllers.responses.TutorPodiumResponse;
import com.knowlink.api.security.models.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/community/podium")
@Tag(name = "Comunidad - Podio", description = "Ranking mensual de tutores destacados")
public interface ICommunityRankingController {

    @GetMapping
    @Operation(summary = "Ver el podio de tutores destacados",
            description = "Ranking del mes en curso, actualizado automáticamente el primer día de cada mes. Se calcula con los "
                    + "mejores aportes, las marcas \"Esto me sirvió\" recibidas y la calificación promedio. Los tres primeros "
                    + "puestos vienen en podium con su insignia (oro, plata, bronce) y el resto del top 10 en ranking.")
    @ApiResponse(responseCode = "200", description = "Podio y tabla del top 10 (pueden venir vacíos si todavía no hay tutores con puntaje)")
    @ApiResponse(responseCode = "401", description = "Sin autenticar")
    @ResponseStatus(OK)
    TutorPodiumResponse getPodium();

    @GetMapping("/me")
    @Operation(summary = "Consultar mi posición en el ranking",
            description = "Para un tutor con rol activo: su puesto actual, aunque no esté entre los 10 primeros, y cuántos puestos "
                    + "le faltan para llegar al top 10 (0 si ya está dentro). Para un usuario sin rol de tutor, eligible es false.")
    @ApiResponse(responseCode = "200", description = "Posición del usuario en el ranking vigente")
    @ApiResponse(responseCode = "401", description = "Sin autenticar")
    @ResponseStatus(OK)
    MyPodiumPositionResponse getMyPosition(@AuthenticationPrincipal UserPrincipal principal);
}
