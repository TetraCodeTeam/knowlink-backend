package com.knowlink.api.users.services.implementations;

import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.mappers.UserMapper;
import com.knowlink.api.users.data.models.Token;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import com.knowlink.api.users.controllers.requests.UpdateUserRequest;
import com.knowlink.api.exceptions.custom_exceptions.*;
import com.knowlink.api.users.events.PasswordResetRequestedEvent;
import com.knowlink.api.users.events.ResendConfirmationEvent;
import com.knowlink.api.users.events.UserRegisteredEvent;
import com.knowlink.api.users.validation.IUserValidationService;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.security.services.JwtService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
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
    private final IStudentProfileRepository studentProfileRepository;
    private final ITutorProfileRepository tutorProfileRepository;

    @Override
    @Transactional
    public User saveStudentUser(StudentRegistrationRequest request) {
        userValidationService.ifEmailAlreadyExistsThrowException(request.email());
        userValidationService.ifDniAlreadyExistsThrowException(request.dni());
        userValidationService.verifyIfPasswordsMatch(request.password(), request.confirmPassword());

        User newUser = userMapper.toStudentUser(request);
        newUser.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(newUser);

        UUID confirmationToken = tokenService.saveUserToken(newUser).getTokenId();
        eventPublisher.publishEvent(new UserRegisteredEvent(newUser, confirmationToken));

        return newUser;
    }

    @Override
    @Transactional
    public User saveTutorUser(TutorRegistrationRequest request) {
        userValidationService.ifEmailAlreadyExistsThrowException(request.email());
        userValidationService.ifDniAlreadyExistsThrowException(request.dni());
        userValidationService.verifyIfPasswordsMatch(request.password(), request.confirmPassword());

        User newUser = userMapper.toTutorUser(request);
        newUser.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(newUser);

        UUID confirmationToken = tokenService.saveUserToken(newUser).getTokenId();
        eventPublisher.publishEvent(new UserRegisteredEvent(newUser, confirmationToken));

        return newUser;
    }

    @Override
    public void checkAvailability(String email, String dni) {
        userValidationService.validateAtLeastOneAvailabilityParam(email, dni);

        if (email != null && !email.isBlank()) {
            userValidationService.ifEmailAlreadyExistsThrowException(email.trim());
        }
        if (dni != null && !dni.isBlank()) {
            userValidationService.ifDniAlreadyExistsThrowException(dni.trim());
        }
    }

    @Override
    public User findByIdOrThrowException(UUID userId) {
        return userRepository.findByUserIdAndAccountStatusNot(userId, AccountStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "USER_NOT_FOUND",
                        "Usuario no encontrado.",
                        String.format("user con id '%s' no existe", userId)));
    }

    @Override
    @Transactional
    public User updateUser(UUID userId, UpdateUserRequest request) {
        User user = findByIdOrThrowException(userId);
        user.setFullName(request.firstName() + " " + request.lastName());
        return userRepository.save(user);
    }

    @Override
    public User findUserByEmailOrThrowException(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "USER_NOT_FOUND",
                        "Usuario no encontrado.",
                        String.format("user con email '%s' no existe", email)));
    }

    @Override
    @Transactional
    public void verifyNewUser(UUID userId, UUID tokenValue) {
        Token token = tokenService.findByTokenOrThrowException(tokenValue);
        userValidationService.validateTokenNotExpired(token);

        User user = token.getUser();

        if (!user.getUserId().equals(userId)) {
            throw new ResourceNotFoundException(
                    "USER_NOT_FOUND",
                    "Usuario no encontrado.",
                    String.format("token pertenece a userId '%s', pero se recibió userId '%s'", user.getUserId(),
                            userId));
        }

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

        Role targetRole = loginRequest.targetRole() != null ? loginRequest.targetRole() : user.getRole();

        if (targetRole == Role.TUTOR && !tutorProfileRepository.existsByUser_UserId(user.getUserId())) {
            throw new ValidationException("No tenés un perfil de tutor activo.");
        }
        if (targetRole == Role.STUDENT && !studentProfileRepository.existsByUser_UserId(user.getUserId())) {
            throw new ValidationException("No tenés un perfil de alumno activo.");
        }

        if (!targetRole.equals(user.getRole())) {
            user.setRole(targetRole);
            userRepository.save(user);
        }

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