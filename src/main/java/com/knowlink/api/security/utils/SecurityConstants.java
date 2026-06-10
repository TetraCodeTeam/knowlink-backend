package com.knowlink.api.security.utils;

public class SecurityConstants {

    private SecurityConstants() {}

    public static final String[] PUBLIC_WHITELIST = {
            "/auth/login",
            "/auth/register",
            "/api/users/*/verify-account",
            "/api/users/resend-verification",
            "/api/users/reset-password/**",
            "/api/v1/public/**",
            "/actuator/health",
    };

    public static final String[] SWAGGER_WHITELIST = {
            "/api-docs",
            "/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
    };
}
