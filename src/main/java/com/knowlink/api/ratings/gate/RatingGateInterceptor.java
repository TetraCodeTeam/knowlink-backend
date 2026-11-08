package com.knowlink.api.ratings.gate;

import com.knowlink.api.exceptions.custom_exceptions.RatingRequiredException;
import com.knowlink.api.ratings.services.interfaces.IRatingGateService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.users.data.models.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.PathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
@Slf4j
public class RatingGateInterceptor implements HandlerInterceptor {

    private static final String PENDING_RATINGS_PATH = "/api/v1/ratings/pending";
    private static final String SUBMIT_RATING_PATH = "/api/v1/bookings/*/ratings";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    private final IRatingGateService ratingGateService;
    private final PathMatcher pathMatcher;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || isExempt(request)) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            return true;
        }

        User user = principal.getUser();
        if (user.getRole() == Role.ADMIN) {
            return true;
        }

        boolean blocked;
        try {
            blocked = ratingGateService.isBlocked(user.getUserId());
        } catch (Exception ex) {
            log.error("Rating gate check failed for user {}: {}", user.getUserId(), ex.getMessage(), ex);
            return true;
        }

        if (blocked) {
            log.debug("Rating gate blocked user {}", user.getUserId());
            throw new RatingRequiredException();
        }
        return true;
    }

    private boolean isExempt(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        if (HttpMethod.GET.matches(method) && pathMatcher.match(PENDING_RATINGS_PATH, uri)) {
            return true;
        }
        if (HttpMethod.POST.matches(method) && pathMatcher.match(SUBMIT_RATING_PATH, uri)) {
            return true;
        }
        return HttpMethod.POST.matches(method) && pathMatcher.match(LOGOUT_PATH, uri);
    }
}
