package com.knowlink.api.users.validation;

import com.knowlink.api.exceptions.custom_exceptions.*;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.Token;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.users.services.interfaces.ITokenService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserValidationService implements IUserValidationService {

    private final IUserRepository userRepository;
    private final ITokenService tokenService;

    @Override
    public void ifEmailAlreadyExistsThrowException(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException(
                    "DUPLICATE_EMAIL",
                    "Este correo ya está registrado.",
                    String.format("user con email '%s' ya existe", email));
        }
    }

    @Override
    public void ifDniAlreadyExistsThrowException(String dni) {
        if (userRepository.existsByDni(dni)) {
            throw new DuplicateResourceException(
                    "DUPLICATE_DNI",
                    "Este DNI ya está registrado.",
                    String.format("user con dni '%s' ya existe", dni));
        }
    }

    @Override
    public void verifyIfPasswordsMatch(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new PasswordsDoNotMatchException();
        }
    }

    @Override
    public void ifUserIsAlreadyActiveThrowException(User user) {
        if (user.getAccountStatus() == AccountStatus.ACTIVE) {
            throw new EmailAlreadyVerifiedException("El usuario ya ha verificado su cuenta.");
        }
    }

    @Override
    public void validateTokenNotExpired(Token token) {
        if (token.getTokenExpirationDate() == null ||
                token.getTokenExpirationDate().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("El enlace ha expirado. Por favor, solicita uno nuevo");
        }
    }

    @Override
    public void validateResendLimit(User user) {
        if (!tokenService.canResendToken(user)) {
            throw new TooManyRequestsException(
                    "Has excedido el límite de reenvíos. Por favor, intenta nuevamente más tarde.");
        }
    }

    @Override
    public void validateAtLeastOneAvailabilityParam(String email, String dni) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasDni = dni != null && !dni.isBlank();

        if (!hasEmail && !hasDni) {
            throw new ValidationException("Debes enviar al menos 'email' o 'dni' para verificar disponibilidad.");
        }
    }
}