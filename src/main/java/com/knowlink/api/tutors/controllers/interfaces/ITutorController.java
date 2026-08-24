package com.knowlink.api.tutors.controllers.interfaces;

import com.knowlink.api.auth.controllers.requests.TutorSubjectRequest;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.controllers.requests.UpdateMinNoticeMinutesRequest;
import com.knowlink.api.tutors.controllers.responses.ActivateStudentRoleResponse;
import com.knowlink.api.tutors.controllers.responses.TutorProfileResponse;
import com.knowlink.api.tutors.controllers.responses.TutorSelfProfileResponse;
import com.knowlink.api.tutors.controllers.responses.TutorSubjectResponse;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.models.TutorSearchResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/tutors")
@Tag(name = "Tutors", description = "Perfiles de tutores")
public interface ITutorController {

        @GetMapping("/{userId}/profile")
        @Operation(summary = "Obtener perfil del tutor", description = "El userId corresponde al usuario (User) con rol TUTOR, no al tutor_profile_id")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Perfil encontrado"),
                        @ApiResponse(responseCode = "401", description = "No autenticado"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
                        @ApiResponse(responseCode = "404", description = "Tutor no encontrado"),
        })
        @ResponseStatus(OK)
        @PreAuthorize("hasRole('STUDENT')")
        TutorProfileResponse getTutorProfile(
                @Parameter(description = "Palabra clave") @PathVariable UUID userId,
                @AuthenticationPrincipal UserPrincipal principal);

        @GetMapping("/me/profile")
        @Operation(summary = "Obtener mi perfil de tutor (vista propia, autenticada)")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Perfil encontrado"),
                        @ApiResponse(responseCode = "401", description = "No autenticado"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
                        @ApiResponse(responseCode = "404", description = "Perfil no encontrado"),
        })
        @ResponseStatus(OK)
        @PreAuthorize("hasRole('TUTOR')")
        TutorSelfProfileResponse getMyProfile(@AuthenticationPrincipal UserPrincipal principal);

        @GetMapping("/search/{query}")
        @Operation(summary = "Buscar tutores", description = "Busca tutores por nombre de materia o nombre completo del tutor. "
                        + "Acepta filtros opcionales combinables con AND (modalidad, compensación, día de disponibilidad, verificados, calificación mínima). "
                        + "Sin filtros se comporta idéntico a la búsqueda base. "
                        + "La respuesta es la misma lista de la búsqueda base (sin envoltorio): si el cliente aplicó filtros y la lista queda vacía, "
                        + "el frontend debe mostrar 'No se encontraron tutores para los filtros seleccionados'.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Búsqueda realizada"),
                        @ApiResponse(responseCode = "400", description = "Filtro inválido (valor de enum no reconocido o calificacionMinima fuera de 1-5)"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        @ResponseStatus(OK)
        @PreAuthorize("hasRole('STUDENT')")
        List<TutorSearchResponse> searchTutor(
                @Parameter(description = "Término de búsqueda (materia o nombre del tutor)") @PathVariable String query,
                @Parameter(description = "Modalidad: VIRTUAL, IN_PERSON o BOTH (VIRTUAL/IN_PERSON incluyen a BOTH)") @RequestParam(required = false) Modality modality,
                @Parameter(description = "Tipo de compensación: FREE o PAID") @RequestParam(required = false) CompensationType compensation,
                @Parameter(description = "Día de la semana con disponibilidad: MONDAY, TUESDAY, ..., SUNDAY") @RequestParam(required = false) DayOfWeek dayOfWeek,
                @Parameter(description = "Solo tutores verificados") @RequestParam(required = false) Boolean verifiedOnly,
                @Parameter(description = "Calificación mínima del tutor (1-5; fuera de rango devuelve 400)") @RequestParam(required = false) Double minRating,
                @AuthenticationPrincipal UserPrincipal principal);

        @PostMapping("/subjects")
        @Operation(summary = "Registrar una materia dictada por el tutor", description = "Crea un TutorSubject para el tutor autenticado. La materia se busca (o se crea) dentro de la carrera del propio tutor.")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Materia registrada"),
                        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
                        @ApiResponse(responseCode = "404", description = "Tutor no encontrado"),
                        @ApiResponse(responseCode = "409", description = "El tutor ya tiene registrada esa materia")
        })
        @ResponseStatus(CREATED)
        @PreAuthorize("hasRole('TUTOR')")
        TutorSubjectResponse createTutorSubject(
                @Valid @RequestBody TutorSubjectRequest request,
                @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal);

        @PutMapping("/me/min-notice-minutes")
        @Operation(summary = "Actualizar la antelación mínima (en minutos) para reservar clases del tutor autenticado")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Antelación mínima actualizada"),
                        @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        @ResponseStatus(OK)
        @PreAuthorize("hasRole('TUTOR')")
        void updateMinNoticeMinutes(
                        @AuthenticationPrincipal UserPrincipal principal,
                        @RequestBody @Valid UpdateMinNoticeMinutesRequest request);
        @GetMapping("/me/min-notice-minutes")
        @Operation(summary = "Obtener la antelación mínima (en minutos) configurada por el tutor autenticado")
        @ResponseStatus(OK)
        UpdateMinNoticeMinutesRequest getMinNoticeMinutes(@AuthenticationPrincipal UserPrincipal principal);

        @PostMapping("/me/activate-student-role")
        @Operation(
                summary = "Activar rol alumno",
                description = "Crea el perfil de alumno si no existe y cambia el rol activo a STUDENT. Devuelve un nuevo JWT con rol STUDENT."
        )
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Rol alumno activado"),
                        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        @ResponseStatus(OK)
        @PreAuthorize("hasRole('TUTOR')")
        ActivateStudentRoleResponse activateStudentRole(@AuthenticationPrincipal UserPrincipal principal);
}