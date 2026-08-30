package com.knowlink.api.bookings.controllers.interfaces;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.bookings.controllers.requests.CreateHoldRequest;
import com.knowlink.api.bookings.controllers.responses.HoldResponse;

import io.swagger.v3.oas.annotations.Operation;
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
    @ResponseStatus(CREATED)
    HoldResponse createHold(
            @PathVariable UUID tutorId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody @Valid CreateHoldRequest request
    );

    @DeleteMapping
    @Operation(summary = "Liberar un horario bloqueado (idempotente)")
    @ResponseStatus(NO_CONTENT)
    void releaseHold(
            @PathVariable UUID tutorId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody @Valid CreateHoldRequest request
    );
}