package com.knowlink.api.ratings.controllers.interfaces;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.ratings.controllers.requests.CreateRatingRequest;
import com.knowlink.api.ratings.controllers.responses.RatingResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.springframework.http.HttpStatus.CREATED;

import java.util.UUID;

@RequestMapping("/api/v1/bookings")
@Tag(name = "Calificaciones", description = "Gestión de calificaciones de clases")
public interface IRatingsController {

    @PostMapping("/{bookingId}/ratings")
    @Operation(summary = "Calificar una sesión realizada de manera definitiva")
    @ApiResponse(responseCode = "201", description = "Calificación registrada")
    @ApiResponse(responseCode = "400", description = "La sesión no está realizada o el puntaje es inválido")
    @ApiResponse(responseCode = "409", description = "El usuario ya calificó esta sesión")
    @ApiResponse(responseCode = "404", description = "La reserva no existe o no pertenece al usuario autenticado")
    @ResponseStatus(CREATED)
    RatingResponse submitRating(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody CreateRatingRequest request);

}
