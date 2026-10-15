package com.knowlink.api.tutors.controllers.interfaces;

import com.knowlink.api.tutors.controllers.responses.TutorRatingHistoryResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/tutors")
@Tag(name = "Tutors", description = "Perfiles de tutores")
public interface ITutorRatingHistoryController {

        @GetMapping("/{tutorId}/ratings")
        @Operation(summary = "Ver historial de calificaciones de un tutor", description = "Devuelve el promedio de calificaciones general y desglosado por materia, "
                        + "más los comentarios de los alumnos ordenados del más reciente al más antiguo. "
                        + "Solo se tienen en cuenta calificaciones de sesiones completadas y visibles. "
                        + "El tutorId corresponde al usuario (User) con rol TUTOR, no al tutor_profile_id.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Historial devuelto (puede estar vacío si el tutor aún no tiene calificaciones)"),
                        @ApiResponse(responseCode = "401", description = "No autenticado"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
                        @ApiResponse(responseCode = "404", description = "Tutor no encontrado"),
        })
        @ResponseStatus(OK)
        @PreAuthorize("hasRole('STUDENT')")
        TutorRatingHistoryResponse getRatingHistory(
                        @Parameter(description = "Id del usuario (User) con rol TUTOR") @PathVariable UUID tutorId,
                        @Parameter(description = "Filtra los comentarios a una materia (los promedios por materia se devuelven siempre completos)") @RequestParam(required = false) UUID subjectId,
                        @Parameter(description = "Página de comentarios (mínimo 0)") @RequestParam(defaultValue = "0") int page,
                        @Parameter(description = "Tamaño de página (máximo 50)") @RequestParam(defaultValue = "10") int size);
}