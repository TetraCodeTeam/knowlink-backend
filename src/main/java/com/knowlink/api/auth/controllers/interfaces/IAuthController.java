package com.knowlink.api.auth.controllers.interfaces;

import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.ReactivateAccountRequest;
import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/reactivate")
    @Operation(summary = "Reactivar cuenta desactivada", description = "Revalida las credenciales de una cuenta "
            + "desactivada temporalmente, la reactiva y devuelve un JWT nuevo. Si la cuenta ya está activa, "
            + "el endpoint es idempotente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta reactivada y sesión iniciada"),
            @ApiResponse(responseCode = "400", description = "Datos de la solicitud inválidos"),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas, cuenta sin confirmar o eliminada")
    })
    @ResponseStatus(OK)
    AuthResponse reactivate(@RequestBody @Valid ReactivateAccountRequest request);

    @PostMapping(value = "/register/student", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Registrar nuevo usuario (alumno)",
            description = "Los datos del registro viajan en la parte 'data' (JSON) y la foto de perfil, "
                    + "opcional, en la parte 'file' (JPG, PNG o WebP, máx. 5 MB).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Alumno registrado"),
            @ApiResponse(responseCode = "400", description = "Datos o archivo inválidos")
    })
    @ResponseStatus(CREATED)
    void registerStudent(
            @RequestPart("data") @Valid StudentRegistrationRequest request,
            @RequestPart(value = "file", required = false) MultipartFile profilePicture);

    @PostMapping(value = "/register/tutor", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Registrar nuevo usuario (tutor)",
            description = "Los datos del registro viajan en la parte 'data' (JSON) y la foto de perfil, "
                    + "opcional, en la parte 'file' (JPG, PNG o WebP, máx. 5 MB).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tutor registrado"),
            @ApiResponse(responseCode = "400", description = "Datos o archivo inválidos")
    })
    @ResponseStatus(CREATED)
    void registerTutor(
            @RequestPart("data") @Valid TutorRegistrationRequest request,
            @RequestPart(value = "file", required = false) MultipartFile profilePicture);

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión", description = "Invalida el token JWT actual del usuario autenticado. "
            + "Los tokens invalidados dejan de ser aceptados para requests posteriores.")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada correctamente")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o ya invalidado")
    @ResponseStatus(NO_CONTENT)
    void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization);
}