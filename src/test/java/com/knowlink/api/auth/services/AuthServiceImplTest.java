/* package com.knowlink.api.auth.services;

import com.knowlink.api.auth.services.implementations.AuthServiceImpl;
import com.knowlink.api.auth.services.interfaces.IAuthService;
import com.knowlink.api.security.services.JwtService;
import com.knowlink.api.security.services.TokenBlacklistService;
import com.knowlink.api.students.services.interfaces.IStudentProfileService;
import com.knowlink.api.tutors.services.interfaces.ITutorProfileService;
import com.knowlink.api.users.services.interfaces.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private IUserService userService;
    @Mock
    private ITutorProfileService tutorProfileService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private IStudentProfileService studentProfileService;
    @Mock
    private JwtService jwtService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private IAuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userService, tutorProfileService, authenticationManager, studentProfileService,
                jwtService, tokenBlacklistService);
    }

    @Test
    @DisplayName("AC-1 - logout extrae jti/userId/exp del token y lo persiste en la blacklist")
    void logout_blacklistsCurrentToken() {
        UUID userId = UUID.randomUUID();
        Date expiration = new Date(System.currentTimeMillis() + 60_000);
        when(jwtService.extractJti("token")).thenReturn("jti-1");
        when(jwtService.extractUserId("token")).thenReturn(userId);
        when(jwtService.extractExpiration("token")).thenReturn(expiration);

        authService.logout("token");

        verify(tokenBlacklistService).blacklist(eq("jti-1"), eq(userId), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("logout usa el exp del token como fecha de expiraci\u00f3n del registro de blacklist")
    void logout_usesTokenExpirationAsExpiresAt() {
        UUID userId = UUID.randomUUID();
        Date expiration = new Date(1_700_000_000_000L);
        when(jwtService.extractJti("token")).thenReturn("jti-1");
        when(jwtService.extractUserId("token")).thenReturn(userId);
        when(jwtService.extractExpiration("token")).thenReturn(expiration);

        authService.logout("token");

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tokenBlacklistService).blacklist(eq("jti-1"), eq(userId), captor.capture());
        LocalDateTime expected = expiration.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        assertThat(captor.getValue()).isEqualTo(expected);
    }
} */