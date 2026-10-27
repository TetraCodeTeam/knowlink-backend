package com.knowlink.api.tutors.statistics.controllers.interfaces;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.statistics.controllers.responses.TutorStatisticsResponse;
import com.knowlink.api.tutors.statistics.data.enums.StatisticsPeriodType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDate;

import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/tutors/statistics")
@Tag(name = "Estadísticas del tutor", description = "Panel de métricas de actividad, reputación, alumnos, horarios e ingresos")
public interface ITutorStatisticsController {

    @GetMapping
    @Operation(summary = "Consultar el panel de estadísticas del tutor para un período",
            description = "Con period=CUSTOM, from y to son obligatorios, no pueden ser futuras y deben estar "
                    + "dentro de los últimos 3 años.")
    @ApiResponse(responseCode = "200", description = "Métricas del período solicitado")
    @ApiResponse(responseCode = "400", description = "Período inválido (fechas faltantes, invertidas, futuras o fuera del límite)")
    @ApiResponse(responseCode = "403", description = "La sección es exclusiva para tutores")
    @ResponseStatus(OK)
    TutorStatisticsResponse getStatistics(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Período rápido o CUSTOM para un rango propio")
            @RequestParam(defaultValue = "LAST_3_MONTHS") StatisticsPeriodType period,
            @Parameter(description = "Inicio del rango (yyyy-MM-dd). Solo con period=CUSTOM")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fin del rango, inclusive (yyyy-MM-dd). Solo con period=CUSTOM")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);
}
