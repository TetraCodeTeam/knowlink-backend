package com.knowlink.api.config;

import com.knowlink.api.security.entrypoint.GlobalAuthenticationEntryPoint;
import com.knowlink.api.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.DispatcherType;

import static com.knowlink.api.security.utils.SecurityConstants.SWAGGER_WHITELIST;
import static com.knowlink.api.security.utils.SecurityConstants.PUBLIC_WHITELIST;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authenticationProvider;
    private final GlobalAuthenticationEntryPoint globalAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(globalAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        // Spring Security 6 también intercepta los dispatches ERROR y ASYNC del
                        // servlet container — sin este permitAll, una respuesta SSE ya comprometida
                        // (streaming en curso) puede terminar en un AccessDenied al reenviarse
                        // internamente para manejar un error, sobre una respuesta que ya empezó a
                        // escribirse. Esto autoriza el *dispatch* interno, no el endpoint: la
                        // autenticación real del SSE se sigue exigiendo en el dispatch REQUEST
                        // original, por el resto de las reglas de este método.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()
                        .requestMatchers(SWAGGER_WHITELIST).permitAll()
                        .requestMatchers(PUBLIC_WHITELIST).permitAll()
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
