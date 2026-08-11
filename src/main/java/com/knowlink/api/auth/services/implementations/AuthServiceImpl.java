package com.knowlink.api.auth.services.implementations;

import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import com.knowlink.api.auth.services.interfaces.IAuthService;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.security.services.JwtService;
import com.knowlink.api.security.services.TokenBlacklistService;
import com.knowlink.api.students.services.interfaces.IStudentProfileService;
import com.knowlink.api.tutors.services.interfaces.ITutorProfileService;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.services.interfaces.IUserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final IUserService userService;
    private final ITutorProfileService tutorProfileService;
    private final AuthenticationManager authenticationManager;
    private final IStudentProfileService studentProfileService;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()));
        return userService.verifyUser(request);
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
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        tokenBlacklistService.blacklist(jti, userId, expiresAt);
    }

    @Override
    @Transactional
    public void registerTutor(TutorRegistrationRequest request) {
        request.subjects().forEach(subject -> {
            if (subject.compensationType() == CompensationType.PAID && (subject.pricePerHour() == null ||
                    subject.pricePerHour().signum() <= 0)) {
                throw new ValidationException(
                        "Price must be greater than zero for subject: " + subject.subjectName());
            }
        });

        User tutor = userService.saveTutorUser(request);
        tutorProfileService.createProfile(tutor, request);
    }
}