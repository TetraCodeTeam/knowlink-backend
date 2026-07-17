package com.knowlink.api.tutors.controllers.interfaces;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.controllers.responses.TutorProfileResponse;
import com.knowlink.api.tutors.controllers.responses.TutorSelfProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

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
                        @PathVariable UUID userId,
                        @AuthenticationPrincipal UserPrincipal principal);

        @GetMapping("/me/profile")
        @Operation(summary = "Obtener mi perfil de tutor (vista propia, autenticada)")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Perfil encontrado"),
                        @ApiResponse(responseCode = "404", description = "Perfil no encontrado"),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado")
        })
        @ResponseStatus(OK)
        @PreAuthorize("hasRole('TUTOR')")
        TutorSelfProfileResponse getMyProfile(@AuthenticationPrincipal UserPrincipal principal);
}