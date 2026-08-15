package com.knowlink.api.students.controllers.interfaces;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.students.controllers.responses.StudentSelfProfileResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/students")
@Tag(name = "Students", description = "Perfil de alumnos")
public interface IStudentController {

    @GetMapping("/me/profile")
    @Operation(summary = "Obtener mi perfil de alumno (vista propia, autenticada)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil encontrado"),
            @ApiResponse(responseCode = "404", description = "Perfil no encontrado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @ResponseStatus(OK)
    @PreAuthorize("hasRole('STUDENT')")
    StudentSelfProfileResponse getMyProfile(@AuthenticationPrincipal UserPrincipal principal);
}
