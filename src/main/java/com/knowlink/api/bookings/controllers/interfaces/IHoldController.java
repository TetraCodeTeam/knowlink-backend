package com.knowlink.api.bookings.controllers.interfaces;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.bookings.controllers.requests.CreateHoldRequest;
import com.knowlink.api.bookings.controllers.responses.HoldResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@RequestMapping("/api/v1/tutors/{tutorId}/booking-slots/hold")
@Tag(name = "Reservas", description = "Bloqueo temporal de horarios antes de confirmar una reserva")
public interface IHoldController {

        @PostMapping
        @Operation(summary = "Bloquear temporalmente un horario (15 min) antes de reservar")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Hold creado"),
                        @ApiResponse(responseCode = "400", description = "Datos inválidos o slot no disponible"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
                        @ApiResponse(responseCode = "404", description = "Tutor o slot no encontrados")
        })
        @ResponseStatus(CREATED)
        HoldResponse createHold(
                        @PathVariable UUID tutorId,
                        @AuthenticationPrincipal UserPrincipal principal,
                        @RequestBody @Valid CreateHoldRequest request);

        @DeleteMapping
        @Operation(summary = "Liberar un horario bloqueado (idempotente)")
        @ApiResponses({
                        @ApiResponse(responseCode = "204", description = "Horario liberado (o ya no había un hold activo para liberar)"),
                        @ApiResponse(responseCode = "400", description = "Datos inválidos en la request"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        @ResponseStatus(NO_CONTENT)
        void releaseHold(
                        @PathVariable UUID tutorId,
                        @AuthenticationPrincipal UserPrincipal principal,
                        @RequestBody @Valid CreateHoldRequest request);
}