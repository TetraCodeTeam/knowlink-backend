package com.knowlink.api.ratings.gate;

import com.knowlink.api.exceptions.custom_exceptions.RatingRequiredException;
import com.knowlink.api.ratings.services.interfaces.IRatingGateService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingGateInterceptorTest {

    @Mock
    private IRatingGateService ratingGateService;

    private PathMatcher pathMatcher;
    private RatingGateInterceptor interceptor;

    @BeforeEach
    void setUp() {
        pathMatcher = new AntPathMatcher();
        interceptor = new RatingGateInterceptor(ratingGateService, pathMatcher);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("usuario bloqueado en un endpoint protegido lanza RatingRequiredException")
    void blockedUser_throwsRatingRequired() {
        User user = user(Role.STUDENT);
        authenticate(user);
        when(ratingGateService.isBlocked(user.getUserId())).thenReturn(true);

        assertThatThrownBy(() -> interceptor.preHandle(
                request("GET", "/api/v1/bookings/mine"), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(RatingRequiredException.class);
    }

    @Test
    @DisplayName("GET /ratings/pending está exento")
    void pendingRatings_isExempt() {
        authenticate(user(Role.STUDENT));

        assertThat(interceptor.preHandle(
                request("GET", "/api/v1/ratings/pending"), new MockHttpServletResponse(), new Object()))
                .isTrue();
        verifyNoInteractions(ratingGateService);
    }

    @Test
    @DisplayName("POST /bookings/{id}/ratings está exento")
    void submitRating_isExempt() {
        authenticate(user(Role.STUDENT));

        assertThat(interceptor.preHandle(
                request("POST", "/api/v1/bookings/" + UUID.randomUUID() + "/ratings"),
                new MockHttpServletResponse(), new Object()))
                .isTrue();
        verifyNoInteractions(ratingGateService);
    }

    @Test
    @DisplayName("POST /auth/logout está exento")
    void logout_isExempt() {
        authenticate(user(Role.STUDENT));

        assertThat(interceptor.preHandle(
                request("POST", "/api/v1/auth/logout"), new MockHttpServletResponse(), new Object()))
                .isTrue();
        verifyNoInteractions(ratingGateService);
    }

    @Test
    @DisplayName("OPTIONS está exento (preflight CORS)")
    void options_isExempt() {
        authenticate(user(Role.STUDENT));

        assertThat(interceptor.preHandle(
                request("OPTIONS", "/api/v1/bookings/mine"), new MockHttpServletResponse(), new Object()))
                .isTrue();
        verifyNoInteractions(ratingGateService);
    }

    @Test
    @DisplayName("ADMIN nunca es bloqueado")
    void admin_isNeverBlocked() {
        authenticate(user(Role.ADMIN));

        assertThat(interceptor.preHandle(
                request("GET", "/api/v1/bookings/mine"), new MockHttpServletResponse(), new Object()))
                .isTrue();
        verifyNoInteractions(ratingGateService);
    }

    @Test
    @DisplayName("sin autenticación el request continúa")
    void unauthenticated_passes() {
        assertThat(interceptor.preHandle(
                request("GET", "/api/v1/bookings/mine"), new MockHttpServletResponse(), new Object()))
                .isTrue();
        verifyNoInteractions(ratingGateService);
    }

    @Test
    @DisplayName("si el chequeo falla, el request continúa (fail-open)")
    void checkFailure_failsOpen() {
        User user = user(Role.STUDENT);
        authenticate(user);
        when(ratingGateService.isBlocked(user.getUserId())).thenThrow(new IllegalStateException("database down"));

        assertThat(interceptor.preHandle(
                request("GET", "/api/v1/bookings/mine"), new MockHttpServletResponse(), new Object()))
                .isTrue();
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(uri);
        return request;
    }

    private User user(Role role) {
        return User.builder().userId(UUID.randomUUID()).role(role).build();
    }

    private void authenticate(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
