package com.knowlink.api.tutors.controllers.interfaces;

import com.knowlink.api.tutors.controllers.responses.TutorProfileResponse;
import com.knowlink.api.tutors.data.models.TutorSearchResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/tutors")
@Tag(name = "Tutors", description = "Perfiles de tutores")
public interface ITutorController {

        @GetMapping("/{userId}/profile")
        @Operation(summary = "Obtener perfil del tutor", description = "El userId corresponde al usuario (User) con rol TUTOR, no al tutor_profile_id")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Perfil encontrado"),
                        @ApiResponse(responseCode = "404", description = "Tutor no encontrado"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        @ResponseStatus(OK)
        @PreAuthorize("hasRole('STUDENT')")
        TutorProfileResponse getTutorProfile(
                        @Parameter(description = "ID del usuario (User) con rol TUTOR") @PathVariable UUID userId,
                        Authentication authentication);

        @GetMapping("/search")
        @Operation(summary = "Buscar tutores", description = "Busca tutores por nombre de materia")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Búsqueda realizada"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        @ResponseStatus(OK)
        @PreAuthorize("hasRole('STUDENT')")
        List<TutorSearchResponse> searchTutor(
                        @Parameter(description = "ID del usuario (User) con rol TUTOR") @PathVariable String query,
                        Authentication authentication);
}