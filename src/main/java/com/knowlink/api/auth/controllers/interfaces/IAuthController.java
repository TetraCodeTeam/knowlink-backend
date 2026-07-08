package com.knowlink.api.auth.controllers.interfaces;

import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.UserRegistrationRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Autenticación y registro de usuarios")
public interface IAuthController {

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión")
    @ResponseStatus(OK)
    AuthResponse login(@RequestBody @Valid LoginRequest request);

    @PostMapping("/register")
    @Operation(summary = "Registrar nuevo usuario (alumno)")
    @ResponseStatus(CREATED)
    void register(@RequestBody @Valid UserRegistrationRequest request);

    @PostMapping("/register/tutor")
    @Operation(summary = "Register new tutor")
    @ApiResponse(responseCode = "201", description = "Tutor registered successfully")
    @ResponseStatus(CREATED)
    void registerTutor(@RequestBody @Valid TutorRegistrationRequest request);
}
