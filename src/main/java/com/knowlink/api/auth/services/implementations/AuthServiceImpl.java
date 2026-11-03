package com.knowlink.api.auth.services.implementations;

import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.ReactivateAccountRequest;
import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import com.knowlink.api.auth.services.interfaces.IAuthService;
import com.knowlink.api.auth.validations.IAuthValidationService;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.services.JwtService;
import com.knowlink.api.security.services.TokenBlacklistService;
import com.knowlink.api.students.services.interfaces.IStudentProfileService;
import com.knowlink.api.tutors.services.interfaces.ITutorProfileService;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.services.interfaces.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final IUserService userService;
    private final ITutorProfileService tutorProfileService;
    private final IStudentProfileService studentProfileService;
    private final IAuthValidationService authValidationService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AuthResponse login(LoginRequest request) {
        // Reveal the deactivated state only when the password is correct (no account enumeration).
        userService.findOptionalByEmail(request.email())
                .ifPresent(user -> authValidationService
                        .ifDeactivatedAccountThrowExceptionOrBadCredentials(user, request.password()));

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()));

        User user = userService.findUserByEmailOrThrowException(request.email());
        return issueSession(user, request.targetRole());
    }

    @Override
    @Transactional
    public AuthResponse reactivate(ReactivateAccountRequest request) {
        User user = userService.findOptionalByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("invalid credentials");
        }

        switch (user.getAccountStatus()) {
            case DEACTIVATED -> user = userService.reactivateAccount(user.getUserId());
            case ACTIVE -> {
                // Idempotent: double click or already reactivated.
            }
            case INACTIVE -> throw new DisabledException("account not confirmed");
            case DELETED -> throw new LockedException("account deleted");
        }

        return issueSession(user, request.targetRole());
    }

    private AuthResponse issueSession(User user, Role requestedRole) {
        Role targetRole = requestedRole != null ? requestedRole : user.getRole();
        authValidationService.ifUserLacksProfileForTargetRoleThrowException(user, targetRole);

        if (!targetRole.equals(user.getRole())) {
            user = userService.updateUserRole(user.getUserId(), targetRole);
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(user.getUserId(), user.getEmail(), token, user.getRole());
    }

    @Override
    @Transactional
    public void registerStudent(StudentRegistrationRequest request) {
        User student = userService.saveStudentUser(request);
        studentProfileService.createProfile(student, request);
    }

    @Override
    public void logout(String token) {
        String jti = jwtService.extractJti(token);
        UUID userId = jwtService.extractUserId(token);
        LocalDateTime expiresAt = jwtService.extractExpiration(token)
                .toInstant()
                .atZone(AppTimeZone.ZONE)
                .toLocalDateTime();
        tokenBlacklistService.blacklist(jti, userId, expiresAt);
    }

    @Override
    @Transactional
    public void registerTutor(TutorRegistrationRequest request) {
        User tutor = userService.saveTutorUser(request);
        tutorProfileService.createProfile(tutor, request);
    }
}