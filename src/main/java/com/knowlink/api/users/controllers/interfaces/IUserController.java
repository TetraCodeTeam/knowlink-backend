package com.knowlink.api.users.controllers.interfaces;

import com.knowlink.api.users.controllers.requests.ConfirmTokenRequest;
import com.knowlink.api.users.controllers.requests.EmailRequest;
import com.knowlink.api.users.controllers.requests.ResetPasswordRequest;
import com.knowlink.api.users.controllers.responses.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User management")
public interface IUserController {

    @GetMapping("/availability")
    @Operation(summary = "Verificar disponibilidad de email y/o DNI para registro")
    @ApiResponse(responseCode = "200", description = "Disponible")
    @ApiResponse(responseCode = "409", description = "Email y/o DNI ya registrado")
    @ResponseStatus(OK)
    void checkAvailability(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String dni);

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID")
    @ResponseStatus(HttpStatus.OK)
    UserResponse getUserById(@PathVariable UUID userId);

    @PostMapping("/{userId}/verify-account")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void verifyAccount(@PathVariable UUID userId,
            @RequestBody @Valid ConfirmTokenRequest confirmTokenRequest);

    @PostMapping("/resend-verification-account")
    @ResponseStatus(HttpStatus.OK)
    void resendConfirmationEmail(@RequestBody @Valid EmailRequest emailRequest);

    @PostMapping("/reset-password/email")
    @Operation(summary = "Solicitar enlace de recuperación de contraseña",
            description = "Endpoint público. Responde siempre 204 sin revelar si el correo está registrado. "
                    + "Si la cuenta existe y está activa, envía un enlace válido por 30 minutos.")
    @ApiResponse(responseCode = "204", description = "Solicitud procesada")
    @ApiResponse(responseCode = "400", description = "Correo vacío o con formato inválido")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void sendResetPasswordEmail(@RequestBody @Valid EmailRequest emailRequest);

    @PostMapping("/reset-password")
    @Operation(summary = "Restablecer contraseña con el enlace de recuperación",
            description = "Endpoint público. El enlace es de un solo uso y vence a los 30 minutos. "
                    + "La nueva contraseña exige mínimo 8 caracteres, una mayúscula, una minúscula y un carácter especial.")
    @ApiResponse(responseCode = "204", description = "Contraseña actualizada")
    @ApiResponse(responseCode = "400", description = "Contraseña inválida o las contraseñas no coinciden")
    @ApiResponse(responseCode = "422", description = "Enlace usado, vencido o inválido (RESET_LINK_INVALID)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@RequestBody @Valid ResetPasswordRequest resetPasswordRequest);
}