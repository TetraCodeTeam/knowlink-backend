package com.knowlink.api.auth.controllers.interfaces;

import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication and user registration")
public interface IAuthController {

    @PostMapping("/login")
    @Operation(summary = "Login")
    @ResponseStatus(OK)
    AuthResponse login(@RequestBody @Valid LoginRequest request);

    @PostMapping("/register/student")
    @Operation(summary = "Registrar nuevo usuario (alumno)")
    @ResponseStatus(CREATED)
    void registerStudent(@RequestBody @Valid StudentRegistrationRequest request);

    @PostMapping("/register/tutor")
    @Operation(summary = "Registrar nuevo usuario (tutor)")
    @ApiResponse(responseCode = "201", description = "Tutor registered successfully")
    @ResponseStatus(CREATED)
    void registerTutor(@RequestBody @Valid TutorRegistrationRequest request);

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión", description = "Invalida el token JWT actual del usuario autenticado. "
            + "Los tokens invalidados dejan de ser aceptados para requests posteriores.")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada correctamente")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o ya invalidado")
    @ResponseStatus(NO_CONTENT)
    void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization);
}
