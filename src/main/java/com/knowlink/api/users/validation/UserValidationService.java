package com.knowlink.api.users.validation;

import com.knowlink.api.exceptions.custom_exceptions.*;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.Token;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.users.services.interfaces.ITokenService;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
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
            throw new DuplicateResourceException("user", "email", email);
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
            throw new EmailAlreadyVerifiedException("The user account has already been verified");
        }
    }

    @Override
    public void validateTokenNotExpired(Token token) {
        if (token.getTokenExpirationDate() == null ||
                token.getTokenExpirationDate().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("The token has expired. Please request a new link");
        }
    }

    @Override
    public void validateResendLimit(User user) {
        if (!tokenService.canResendToken(user)) {
            throw new TooManyRequestsException(
                    "You have exceeded the resend limit. Please try again later"
            );
        }
    }
}