package com.knowlink.api.tutors.schedule.controllers.interfaces;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.schedule.controllers.responses.WeeklyScheduleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDate;

@RequestMapping("/api/v1/tutors/me/weekly-schedule")
@Tag(name = "Tutor Weekly Schedule", description = "Agenda semanal del tutor: disponibilidad y reservas en una vista unificada")
@PreAuthorize("hasRole('TUTOR')")
public interface ITutorWeeklyScheduleController {

    @GetMapping
    @Operation(summary = "Obtener la agenda semanal del tutor autenticado con bloques de disponibilidad y reservas")
    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agenda semanal devuelta exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permiso de tutor")
    })
    WeeklyScheduleResponse getWeeklySchedule(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);
}
