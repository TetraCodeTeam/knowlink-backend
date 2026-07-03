package com.knowlink.api.users.services.implementations;

import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.mappers.UserMapper;
import com.knowlink.api.users.data.models.Token;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.UserRegistrationRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import com.knowlink.api.exceptions.custom_exceptions.*;
import com.knowlink.api.users.events.PasswordResetRequestedEvent;
import com.knowlink.api.users.events.ResendConfirmationEvent;
import com.knowlink.api.users.events.UserRegisteredEvent;
import com.knowlink.api.users.validation.IUserValidationService;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.security.services.JwtService;
import com.knowlink.api.users.services.interfaces.ITokenService;
import com.knowlink.api.users.services.interfaces.IUserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final IUserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ITokenService tokenService;
    private final JwtService jwtService;
    private final IUserValidationService userValidationService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void saveUser(UserRegistrationRequest request) {
        userValidationService.ifEmailAlreadyExistsThrowException(request.email());
        userValidationService.verifyIfPasswordsMatch(request.password(), request.confirmPassword());

        User newUser = userMapper.toUser(request);
        newUser.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(newUser);

        UUID confirmationToken = tokenService.saveUserToken(newUser).getTokenId();
        eventPublisher.publishEvent(new UserRegisteredEvent(newUser, confirmationToken));
    }

    @Override
    public User findByIdOrThrowException(UUID userId) {
        return userRepository.findByUserIdAndAccountStatusNot(userId, AccountStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    @Override
    public User findUserByEmailOrThrowException(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Override
    @Transactional
    public void verifyNewUser(UUID userId, UUID tokenValue) {
        Token token = tokenService.findByTokenOrThrowException(tokenValue);
        userValidationService.validateTokenNotExpired(token);

        User user = token.getUser();

        if (user.getAccountStatus() == AccountStatus.ACTIVE) {
            return;
        }

        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(user);
        tokenService.deleteTokensByUser(user);
    }

    @Override
    @Transactional
    public void resendConfirmationEmail(String email) {
        User user = findUserByEmailOrThrowException(email);
        userValidationService.ifUserIsAlreadyActiveThrowException(user);
        userValidationService.validateResendLimit(user);

        Token token = tokenService.saveUserToken(user);
        eventPublisher.publishEvent(new ResendConfirmationEvent(user, token.getTokenId()));
    }

    @Override
    public AuthResponse verifyUser(LoginRequest loginRequest) {
        User user = findUserByEmailOrThrowException(loginRequest.email());
        String token = jwtService.generateToken(user);
        return new AuthResponse(user.getUserId(), user.getEmail(), token, user.getRole());
    }

    @Override
    @Transactional
    public void resetPassword(UUID token, String newPassword, String confirmNewPassword) {
        userValidationService.verifyIfPasswordsMatch(newPassword, confirmNewPassword);
        Token resetToken = tokenService.findByTokenOrThrowException(token);
        userValidationService.validateTokenNotExpired(resetToken);

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenService.deleteTokensByUser(user);
    }

    @Override
    @Transactional
    public void sendResetPasswordEmail(String email) {
        User user = findUserByEmailOrThrowException(email);
        userValidationService.validateResendLimit(user);

        Token token = tokenService.saveUserToken(user);
        eventPublisher.publishEvent(new PasswordResetRequestedEvent(user, token.getTokenId()));
    }
}