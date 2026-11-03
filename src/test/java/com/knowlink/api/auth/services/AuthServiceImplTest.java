package com.knowlink.api.auth.services;

import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.ReactivateAccountRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import com.knowlink.api.auth.services.implementations.AuthServiceImpl;
import com.knowlink.api.auth.validations.IAuthValidationService;
import com.knowlink.api.exceptions.custom_exceptions.AccountDeactivatedException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.services.JwtService;
import com.knowlink.api.security.services.TokenBlacklistService;
import com.knowlink.api.students.services.interfaces.IStudentProfileService;
import com.knowlink.api.tutors.services.interfaces.ITutorProfileService;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.services.interfaces.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String EMAIL = "user@test.com";
    private static final String RAW_PASSWORD = "Password123!";
    private static final String ENCODED_PASSWORD = "encoded";

    @Mock
    private IUserService userService;
    @Mock
    private ITutorProfileService tutorProfileService;
    @Mock
    private IStudentProfileService studentProfileService;
    @Mock
    private IAuthValidationService authValidationService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthServiceImpl authService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userService, tutorProfileService, studentProfileService,
                authValidationService, authenticationManager, jwtService, tokenBlacklistService,
                passwordEncoder);
        userId = UUID.randomUUID();
    }

    private User user(AccountStatus status, Role role) {
        return User.builder()
                .userId(userId)
                .email(EMAIL)
                .password(ENCODED_PASSWORD)
                .role(role)
                .accountStatus(status)
                .build();
    }

    private LoginRequest loginRequest() {
        return new LoginRequest(EMAIL, RAW_PASSWORD, null);
    }

    private ReactivateAccountRequest reactivateRequest(Role targetRole) {
        return new ReactivateAccountRequest(EMAIL, RAW_PASSWORD, targetRole);
    }

    @Test
    @DisplayName("login of a DEACTIVATED account with the right password throws AccountDeactivatedException")
    void login_deactivatedWithCorrectPassword_throwsAccountDeactivated() {
        User deactivated = user(AccountStatus.DEACTIVATED, Role.STUDENT);
        when(userService.findOptionalByEmail(EMAIL)).thenReturn(Optional.of(deactivated));
        doThrow(new AccountDeactivatedException("deactivated"))
                .when(authValidationService)
                .ifDeactivatedAccountThrowExceptionOrBadCredentials(deactivated, RAW_PASSWORD);

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(AccountDeactivatedException.class);

        verifyNoInteractions(authenticationManager);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("login of a DEACTIVATED account with a wrong password throws BadCredentialsException")
    void login_deactivatedWithWrongPassword_throwsBadCredentials() {
        User deactivated = user(AccountStatus.DEACTIVATED, Role.STUDENT);
        when(userService.findOptionalByEmail(EMAIL)).thenReturn(Optional.of(deactivated));
        doThrow(new BadCredentialsException("invalid credentials"))
                .when(authValidationService)
                .ifDeactivatedAccountThrowExceptionOrBadCredentials(deactivated, RAW_PASSWORD);

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(authenticationManager);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("login of an ACTIVE account keeps the previous behavior")
    void login_active_returnsAuthResponse() {
        User active = user(AccountStatus.ACTIVE, Role.STUDENT);
        when(userService.findOptionalByEmail(EMAIL)).thenReturn(Optional.of(active));
        when(userService.findUserByEmailOrThrowException(EMAIL)).thenReturn(active);
        when(jwtService.generateToken(active)).thenReturn("jwt");

        AuthResponse response = authService.login(loginRequest());

        verify(authenticationManager).authenticate(any());
        assertThat(response.token()).isEqualTo("jwt");
        assertThat(response.role()).isEqualTo(Role.STUDENT);
        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("login with an unknown email still fails with BadCredentials")
    void login_unknownEmail_throwsBadCredentials() {
        when(userService.findOptionalByEmail(EMAIL)).thenReturn(Optional.empty());
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("reactivate a DEACTIVATED account sets it ACTIVE and returns a new JWT")
    void reactivate_deactivated_returnsAuthResponse() {
        User deactivated = user(AccountStatus.DEACTIVATED, Role.STUDENT);
        User active = user(AccountStatus.ACTIVE, Role.STUDENT);
        when(userService.findOptionalByEmail(EMAIL)).thenReturn(Optional.of(deactivated));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(userService.reactivateAccount(userId)).thenReturn(active);
        when(jwtService.generateToken(active)).thenReturn("new-jwt");

        AuthResponse response = authService.reactivate(reactivateRequest(null));

        verify(userService).reactivateAccount(userId);
        assertThat(response.token()).isEqualTo("new-jwt");
        assertThat(response.role()).isEqualTo(Role.STUDENT);
    }

    @Test
    @DisplayName("reactivate with a wrong password throws BadCredentials and never saves")
    void reactivate_wrongPassword_throwsBadCredentials() {
        User deactivated = user(AccountStatus.DEACTIVATED, Role.STUDENT);
        when(userService.findOptionalByEmail(EMAIL)).thenReturn(Optional.of(deactivated));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

        assertThatThrownBy(() -> authService.reactivate(reactivateRequest(null)))
                .isInstanceOf(BadCredentialsException.class);

        verify(userService, never()).reactivateAccount(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("reactivate with an unknown email throws BadCredentials")
    void reactivate_unknownEmail_throwsBadCredentials() {
        when(userService.findOptionalByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reactivate(reactivateRequest(null)))
                .isInstanceOf(BadCredentialsException.class);

        verify(userService, never()).reactivateAccount(any());
    }

    @Test
    @DisplayName("reactivate an already ACTIVE account is idempotent")
    void reactivate_active_isIdempotent() {
        User active = user(AccountStatus.ACTIVE, Role.STUDENT);
        when(userService.findOptionalByEmail(EMAIL)).thenReturn(Optional.of(active));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(jwtService.generateToken(active)).thenReturn("jwt");

        AuthResponse response = authService.reactivate(reactivateRequest(null));

        assertThat(response.token()).isEqualTo("jwt");
        verify(userService, never()).reactivateAccount(any());
    }

    @Test
    @DisplayName("reactivate an INACTIVE account (email not confirmed) throws DisabledException")
    void reactivate_inactive_throwsDisabled() {
        User inactive = user(AccountStatus.INACTIVE, Role.STUDENT);
        when(userService.findOptionalByEmail(EMAIL)).thenReturn(Optional.of(inactive));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

        assertThatThrownBy(() -> authService.reactivate(reactivateRequest(null)))
                .isInstanceOf(DisabledException.class);

        verify(userService, never()).reactivateAccount(any());
    }

    @Test
    @DisplayName("reactivate a DELETED account throws LockedException")
    void reactivate_deleted_throwsLocked() {
        User deleted = user(AccountStatus.DELETED, Role.STUDENT);
        when(userService.findOptionalByEmail(EMAIL)).thenReturn(Optional.of(deleted));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

        assertThatThrownBy(() -> authService.reactivate(reactivateRequest(null)))
                .isInstanceOf(LockedException.class);

        verify(userService, never()).reactivateAccount(any());
    }

    @Test
    @DisplayName("reactivate with a targetRole the user lacks a profile for fails without issuing a token")
    void reactivate_targetRoleWithoutProfile_throwsValidationAndDoesNotIssueToken() {
        User deactivated = user(AccountStatus.DEACTIVATED, Role.STUDENT);
        User active = user(AccountStatus.ACTIVE, Role.STUDENT);
        when(userService.findOptionalByEmail(EMAIL)).thenReturn(Optional.of(deactivated));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(userService.reactivateAccount(userId)).thenReturn(active);
        doThrow(new ValidationException("No tenés un perfil de tutor activo."))
                .when(authValidationService)
                .ifUserLacksProfileForTargetRoleThrowException(active, Role.TUTOR);

        assertThatThrownBy(() -> authService.reactivate(reactivateRequest(Role.TUTOR)))
                .isInstanceOf(ValidationException.class);

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("logout blacklists the current token")
    void logout_blacklistsCurrentToken() {
        when(jwtService.extractJti("token")).thenReturn("jti-1");
        when(jwtService.extractUserId("token")).thenReturn(userId);
        when(jwtService.extractExpiration("token"))
                .thenReturn(new java.util.Date(System.currentTimeMillis() + 60_000));

        authService.logout("token");

        verify(tokenBlacklistService).blacklist(any(), any(), any());
    }
}