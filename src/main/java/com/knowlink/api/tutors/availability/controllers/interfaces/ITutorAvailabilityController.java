package com.knowlink.api.tutors.availability.controllers.interfaces;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.availability.controllers.requests.SaveAvailabilityBlocksRequest;
import com.knowlink.api.tutors.availability.controllers.responses.AvailabilityBlockResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/tutors/me/availability-blocks")
@Tag(name = "Tutor Availability", description = "Gestión de disponibilidad horaria del tutor")
public interface ITutorAvailabilityController {

    @PutMapping
    @Operation(summary = "Guardar (reemplazar) los bloques de disponibilidad de una semana específica del tutor autenticado")
    @ResponseStatus(OK)
    List<AvailabilityBlockResponse> saveWeekBlocks(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekEnd,
            @RequestBody @Valid SaveAvailabilityBlocksRequest request
    );

    @GetMapping
    @Operation(summary = "Obtener los bloques de disponibilidad del tutor autenticado en un rango de fechas")
    @ResponseStatus(OK)
    List<AvailabilityBlockResponse> getBlocksInRange(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    );
}