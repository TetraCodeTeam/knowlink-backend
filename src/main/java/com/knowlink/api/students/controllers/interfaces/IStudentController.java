package com.knowlink.api.students.controllers.interfaces;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.students.controllers.requests.ActivateTutorRoleRequest;
import com.knowlink.api.students.controllers.responses.ActivateTutorRoleResponse;
import com.knowlink.api.students.controllers.responses.StudentSelfProfileResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/me/activate-tutor-role")
    @Operation(
            summary = "Activar rol tutor",
            description = "Reactiva el rol tutor para un alumno con perfil de tutor previo, o indica que debe completar el registro. Devuelve un nuevo JWT con rol TUTOR."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol tutor activado"),
            @ApiResponse(responseCode = "400", description = "Materias requeridas en la primera activación"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @ResponseStatus(OK)
    @PreAuthorize("hasRole('STUDENT')")
    ActivateTutorRoleResponse activateTutorRole(
            @RequestBody(required = false) @Valid ActivateTutorRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal);

    @PostMapping(value = "/me/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Subir foto de perfil del alumno",
            description = "Sube una imagen de perfil a Supabase Storage y la asocia al perfil del alumno autenticado. "
                    + "Se aceptan JPG, PNG y WebP (máx. 5 MB)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Foto de perfil actualizada"),
            @ApiResponse(responseCode = "400", description = "Archivo inválido (tamaño o formato)"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @ResponseStatus(OK)
    @PreAuthorize("hasRole('STUDENT')")
    StudentSelfProfileResponse uploadProfilePicture(
            @Parameter(description = "Imagen de perfil") @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal);
}
