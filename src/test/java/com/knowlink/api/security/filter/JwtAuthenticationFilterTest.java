package com.knowlink.api.security.filter;

import com.knowlink.api.security.services.JwtService;
import com.knowlink.api.security.services.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.util.PathMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String PROTECTED_PATH = "/api/v1/users";

    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private PathMatcher pathMatcher;
    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;
    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(
                jwtService, userDetailsService, pathMatcher, handlerExceptionResolver, tokenBlacklistService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithBearer(String jwt) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(PROTECTED_PATH);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
        return request;
    }

    private FilterChain mockChain() {
        return new MockFilterChain();
    }

    @Test
    @DisplayName("AC-1 - Rechaza un token cuyo jti est\u00e1 en la blacklist (no autentica)")
    void rejectsTokenWhenJtiIsBlacklisted() throws ServletException, IOException {
        String jwt = "blacklisted-jwt";
        UserDetails userDetails = mock(UserDetails.class);
        when(pathMatcher.match(anyString(), anyString())).thenReturn(false);
        when(jwtService.extractUsername(jwt)).thenReturn("user@test.com");
        when(userDetailsService.loadUserByUsername("user@test.com")).thenReturn(userDetails);
        when(jwtService.extractJti(jwt)).thenReturn("jti-1");
        when(tokenBlacklistService.isBlacklisted("jti-1")).thenReturn(true);
        when(jwtService.isTokenValid(jwt, userDetails)).thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestWithBearer(jwt), response, mockChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Token no blacklisteado y v\u00e1lido autentica correctamente")
    void authenticatesTokenNotInBlacklist() throws ServletException, IOException {
        String jwt = "valid-jwt";
        UserDetails userDetails = mock(UserDetails.class);
        when(pathMatcher.match(anyString(), anyString())).thenReturn(false);
        when(jwtService.extractUsername(jwt)).thenReturn("user@test.com");
        when(userDetailsService.loadUserByUsername("user@test.com")).thenReturn(userDetails);
        when(jwtService.extractJti(jwt)).thenReturn("jti-2");
        when(tokenBlacklistService.isBlacklisted("jti-2")).thenReturn(false);
        when(jwtService.isTokenValid(jwt, userDetails)).thenReturn(true);
        when(userDetails.getAuthorities()).thenReturn(Collections.emptyList());

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestWithBearer(jwt), response, mockChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userDetails);
    }

    @Test
    @DisplayName("No consulta la blacklist cuando el token no trae claim jti")
    void skipsBlacklistCheckWhenJtiIsNull() throws ServletException, IOException {
        String jwt = "legacy-jwt";
        UserDetails userDetails = mock(UserDetails.class);
        when(pathMatcher.match(anyString(), anyString())).thenReturn(false);
        when(jwtService.extractUsername(jwt)).thenReturn("user@test.com");
        when(userDetailsService.loadUserByUsername("user@test.com")).thenReturn(userDetails);
        when(jwtService.extractJti(jwt)).thenReturn(null);
        when(jwtService.isTokenValid(jwt, userDetails)).thenReturn(true);
        when(userDetails.getAuthorities()).thenReturn(Collections.emptyList());

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestWithBearer(jwt), response, mockChain());

        verify(tokenBlacklistService, never()).isBlacklisted(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
}