package com.knowlink.api.tutors.controllers;

import com.knowlink.api.tutors.data.dto.responses.TutorProfileResponse;
import com.knowlink.api.tutors.services.interfaces.TutorProfileService;
import com.knowlink.api.users.data.models.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tutors")
@RequiredArgsConstructor
public class TutorController {

    private final TutorProfileService tutorProfileService;

    @Operation(summary = "Obtener perfil público del tutor")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil encontrado"),
            @ApiResponse(responseCode = "404", description = "Tutor no encontrado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @GetMapping("/{tutorId}/profile")
    @PreAuthorize("hasRole('ALUMNO')")
    public ResponseEntity<TutorProfileResponse> getTutorProfile(@PathVariable UUID tutorId, Authentication authentication) {
        UUID alumnoId = null;
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            alumnoId = ((User) authentication.getPrincipal()).getUserId();
        }

        TutorProfileResponse response = tutorProfileService.getTutorProfile(tutorId, alumnoId);
        return ResponseEntity.ok(response);
    }
}
