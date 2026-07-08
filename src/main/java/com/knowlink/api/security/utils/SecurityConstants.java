package com.knowlink.api.security.utils;

public class SecurityConstants {

        private SecurityConstants() {
        }

        public static final String[] PUBLIC_WHITELIST = {
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/register/tutor",
                "/api/v1/users/*/verify-account",
                "/api/v1/users/resend-verification-account",
                "/api/v1/users/reset-password/**",
                "/api/v1/public/**",
                "/actuator/health",
                "/api/v1/careers",
                "/api/v1/subjects",
                "/api/v1/subjects/basic",
        };

        public static final String[] SWAGGER_WHITELIST = {
                "/api-docs",
                "/api-docs/**",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs/**",
        };
}
