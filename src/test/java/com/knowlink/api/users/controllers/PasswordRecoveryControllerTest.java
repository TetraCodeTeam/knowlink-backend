package com.knowlink.api.users.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.enums.TokenType;
import com.knowlink.api.users.data.models.Token;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.events.PasswordResetRequestedEvent;
import com.knowlink.api.users.repositories.ITokenRepository;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.users.services.interfaces.ITokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class PasswordRecoveryControllerTest {

    private static final String RESET_EMAIL_URL = "/api/v1/users/reset-password/email";
    private static final String RESET_URL = "/api/v1/users/reset-password";
    private static final String VALID_PASSWORD = "Nueva#123";
    private static final String OLD_PASSWORD = "OldPass#1";
    private static final String INVALID_LINK_MESSAGE = "Este enlace ya no es válido. Solicitá uno nuevo";
    private static final String INVALID_LINK_DETAIL = "RESET_LINK_INVALID";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ITokenRepository tokenRepository;

    @Autowired
    private ITokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ApplicationEvents events;

    private User activeUser;
    private User inactiveUser;
    private User deletedUser;

    @BeforeEach
    void seedData() {
        activeUser = userRepository.save(User.builder()
                .firstName("Ana")
                .lastName("Reset")
                .fullName("Ana Reset")
                .email("ana.reset@test.com")
                .password(passwordEncoder.encode(OLD_PASSWORD))
                .dni("31000111")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        inactiveUser = userRepository.save(User.builder()
                .firstName("Beto")
                .lastName("Reset")
                .fullName("Beto Reset")
                .email("beto.reset@test.com")
                .password(passwordEncoder.encode(OLD_PASSWORD))
                .dni("31000222")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.INACTIVE)
                .build());

        deletedUser = userRepository.save(User.builder()
                .firstName("Caro")
                .lastName("Reset")
                .fullName("Caro Reset")
                .email("caro.reset@test.com")
                .password(passwordEncoder.encode(OLD_PASSWORD))
                .dni("31000333")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.DELETED)
                .build());
    }

    private String json(Map<String, Object> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private Map<String, Object> emailBody(String email) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        return payload;
    }

    private Map<String, Object> resetBody(String token, String newPassword, String confirmNewPassword) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("newPassword", newPassword);
        payload.put("confirmNewPassword", confirmNewPassword);
        return payload;
    }

    private ResultActions requestResetEmail(Map<String, Object> payload) throws Exception {
        return mockMvc.perform(post(RESET_EMAIL_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(payload)));
    }

    private ResultActions resetPassword(Map<String, Object> payload) throws Exception {
        return mockMvc.perform(post(RESET_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(payload)));
    }

    private Token createToken(User user, TokenType type, LocalDateTime expiration) {
        return tokenRepository.save(Token.builder()
                .user(user)
                .tokenType(type)
                .tokenExpirationDate(expiration)
                .build());
    }

    private List<Token> resetTokensOf(User user) {
        entityManager.flush();
        return tokenRepository.findAll().stream()
                .filter(token -> token.getTokenType() == TokenType.PASSWORD_RESET)
                .filter(token -> token.getUser().getUserId().equals(user.getUserId()))
                .toList();
    }

    private User reloadUser(User user) {
        entityManager.flush();
        entityManager.clear();
        return userRepository.findById(user.getUserId()).orElseThrow();
    }

    @Test
    @DisplayName("CP-034.1 - 204 con email ACTIVE: crea un token PASSWORD_RESET y publica el evento")
    void requestResetEmail_activeUser_createsResetTokenAndEvent() throws Exception {
        requestResetEmail(emailBody(activeUser.getEmail()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        List<Token> tokens = resetTokensOf(activeUser);
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getTokenType()).isEqualTo(TokenType.PASSWORD_RESET);

        assertThat(events.stream(PasswordResetRequestedEvent.class).count()).isEqualTo(1);
        UUID eventTokenId = events.stream(PasswordResetRequestedEvent.class)
                .findFirst()
                .orElseThrow()
                .tokenId();
        assertThat(eventTokenId).isEqualTo(tokens.get(0).getTokenId());
    }

    @Test
    @DisplayName("CP-034.2 - El token PASSWORD_RESET vence a los 30 minutos y el de confirmación a las 24 horas")
    void requestResetEmail_tokenExpiration() throws Exception {
        requestResetEmail(emailBody(activeUser.getEmail())).andExpect(status().isNoContent());

        Token resetToken = resetTokensOf(activeUser).get(0);
        assertThat(Duration.between(resetToken.getCreatedAt(), resetToken.getTokenExpirationDate()))
                .isEqualTo(Duration.ofMinutes(30));

        Token confirmationToken = tokenService.saveUserToken(activeUser);
        assertThat(Duration.between(confirmationToken.getCreatedAt(), confirmationToken.getTokenExpirationDate()))
                .isEqualTo(Duration.ofHours(24));
        assertThat(confirmationToken.getTokenType()).isEqualTo(TokenType.ACCOUNT_CONFIRMATION);
    }

    @Test
    @DisplayName("CP-034.3 - 204 idéntico con email inexistente: sin token ni evento")
    void requestResetEmail_unknownEmail_returnsSameEmptyResponse() throws Exception {
        requestResetEmail(emailBody("nadie.reset@test.com"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(tokenRepository.count()).isZero();
        assertThat(events.stream(PasswordResetRequestedEvent.class).count()).isZero();
    }

    @Test
    @DisplayName("CP-034.4 - 204 con usuario INACTIVE: sin token ni evento")
    void requestResetEmail_inactiveUser_doesNothing() throws Exception {
        requestResetEmail(emailBody(inactiveUser.getEmail()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(tokenRepository.count()).isZero();
        assertThat(events.stream(PasswordResetRequestedEvent.class).count()).isZero();
    }

    @Test
    @DisplayName("CP-034.5 - Límite excedido: 204 (no 429), sin token nuevo ni evento")
    void requestResetEmail_rateLimitReached_returns204WithoutToken() throws Exception {
        LocalDateTime expiration = LocalDateTime.now().plusMinutes(30);
        createToken(activeUser, TokenType.PASSWORD_RESET, expiration);
        createToken(activeUser, TokenType.PASSWORD_RESET, expiration);
        createToken(activeUser, TokenType.PASSWORD_RESET, expiration);

        requestResetEmail(emailBody(activeUser.getEmail()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(resetTokensOf(activeUser)).hasSize(3);
        assertThat(events.stream(PasswordResetRequestedEvent.class).count()).isZero();
    }

    @Test
    @DisplayName("CP-034.6 - 400 VALIDATION_ERROR con email vacío, con formato inválido o ausente")
    void requestResetEmail_invalidEmail_returns400() throws Exception {
        requestResetEmail(emailBody(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));

        requestResetEmail(emailBody("abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));

        requestResetEmail(Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("CP-034.7 - Reset OK: 204, contraseña actualizada y tokens PASSWORD_RESET eliminados")
    void resetPassword_validToken_updatesPasswordAndDeletesResetTokens() throws Exception {
        Token token = createToken(activeUser, TokenType.PASSWORD_RESET, LocalDateTime.now().plusMinutes(30));

        resetPassword(resetBody(token.getTokenId().toString(), VALID_PASSWORD, VALID_PASSWORD))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        User reloaded = reloadUser(activeUser);
        assertThat(passwordEncoder.matches(VALID_PASSWORD, reloaded.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, reloaded.getPassword())).isFalse();
        assertThat(resetTokensOf(activeUser)).isEmpty();
    }

    @Test
    @DisplayName("CP-034.8 - Reusar el mismo token: 422 RESET_LINK_INVALID")
    void resetPassword_reusedToken_returns422() throws Exception {
        Token token = createToken(activeUser, TokenType.PASSWORD_RESET, LocalDateTime.now().plusMinutes(30));

        resetPassword(resetBody(token.getTokenId().toString(), VALID_PASSWORD, VALID_PASSWORD))
                .andExpect(status().isNoContent());

        resetPassword(resetBody(token.getTokenId().toString(), VALID_PASSWORD, VALID_PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(INVALID_LINK_MESSAGE))
                .andExpect(jsonPath("$.detail").value(INVALID_LINK_DETAIL));
    }

    @Test
    @DisplayName("CP-034.9 - Token vencido: 422 y la contraseña no cambia")
    void resetPassword_expiredToken_returns422() throws Exception {
        Token token = createToken(activeUser, TokenType.PASSWORD_RESET, LocalDateTime.now().minusMinutes(1));

        resetPassword(resetBody(token.getTokenId().toString(), VALID_PASSWORD, VALID_PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(INVALID_LINK_MESSAGE))
                .andExpect(jsonPath("$.detail").value(INVALID_LINK_DETAIL));

        assertThat(passwordEncoder.matches(OLD_PASSWORD, reloadUser(activeUser).getPassword())).isTrue();
        assertThat(tokenRepository.findByTokenId(token.getTokenId())).isPresent();
    }

    @Test
    @DisplayName("CP-034.10 - Token inexistente o malformado: 422 con el mismo mensaje, nunca 500")
    void resetPassword_unknownOrMalformedToken_returns422() throws Exception {
        resetPassword(resetBody(UUID.randomUUID().toString(), VALID_PASSWORD, VALID_PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(INVALID_LINK_MESSAGE))
                .andExpect(jsonPath("$.detail").value(INVALID_LINK_DETAIL));

        resetPassword(resetBody("abc", VALID_PASSWORD, VALID_PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(INVALID_LINK_MESSAGE))
                .andExpect(jsonPath("$.detail").value(INVALID_LINK_DETAIL));

        assertThat(passwordEncoder.matches(OLD_PASSWORD, reloadUser(activeUser).getPassword())).isTrue();
    }

    @Test
    @DisplayName("CP-034.11 - Los tokens de reset y confirmación no son intercambiables")
    void tokensAreNotInterchangeable() throws Exception {
        Token confirmationToken = createToken(activeUser, TokenType.ACCOUNT_CONFIRMATION,
                LocalDateTime.now().plusHours(24));

        resetPassword(resetBody(confirmationToken.getTokenId().toString(), VALID_PASSWORD, VALID_PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(INVALID_LINK_DETAIL));

        Token resetToken = createToken(inactiveUser, TokenType.PASSWORD_RESET, LocalDateTime.now().plusMinutes(30));
        Map<String, Object> verifyPayload = new LinkedHashMap<>();
        verifyPayload.put("token", resetToken.getTokenId().toString());

        mockMvc.perform(post("/api/v1/users/" + inactiveUser.getUserId() + "/verify-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(verifyPayload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("TOKEN_NOT_FOUND"));

        assertThat(reloadUser(inactiveUser).getAccountStatus()).isEqualTo(AccountStatus.INACTIVE);
    }

    @Test
    @DisplayName("CP-034.12 - Contraseñas débiles: 400 VALIDATION_ERROR, sin cambios y token vigente")
    void resetPassword_weakPasswords_returns400() throws Exception {
        Token token = createToken(activeUser, TokenType.PASSWORD_RESET, LocalDateTime.now().plusMinutes(30));

        for (String weakPassword : List.of("Ab#1", "nueva#123", "NUEVA#123", "Nueva1234")) {
            resetPassword(resetBody(token.getTokenId().toString(), weakPassword, weakPassword))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));

            assertThat(passwordEncoder.matches(OLD_PASSWORD, reloadUser(activeUser).getPassword())).isTrue();
            assertThat(tokenRepository.findByTokenId(token.getTokenId())).isPresent();
        }
    }

    @Test
    @DisplayName("CP-034.13 - Contraseñas distintas: 400 PASSWORDS_MISMATCH y token intacto")
    void resetPassword_passwordsDoNotMatch_returns400() throws Exception {
        Token token = createToken(activeUser, TokenType.PASSWORD_RESET, LocalDateTime.now().plusMinutes(30));

        resetPassword(resetBody(token.getTokenId().toString(), VALID_PASSWORD, "Otra#123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("PASSWORDS_MISMATCH"));

        assertThat(passwordEncoder.matches(OLD_PASSWORD, reloadUser(activeUser).getPassword())).isTrue();
        assertThat(tokenRepository.findByTokenId(token.getTokenId())).isPresent();
    }

    @Test
    @DisplayName("CP-034.14 - Body vacío o sin token: 400, nunca 500")
    void resetPassword_missingFields_returns400() throws Exception {
        resetPassword(Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));

        Map<String, Object> withoutToken = new LinkedHashMap<>();
        withoutToken.put("newPassword", VALID_PASSWORD);
        withoutToken.put("confirmNewPassword", VALID_PASSWORD);

        resetPassword(withoutToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("CP-034.15 - El reset elimina solo los tokens PASSWORD_RESET del usuario")
    void resetPassword_deletesOnlyTokensOfThatUser() throws Exception {
        Token first = createToken(activeUser, TokenType.PASSWORD_RESET, LocalDateTime.now().plusMinutes(30));
        createToken(activeUser, TokenType.PASSWORD_RESET, LocalDateTime.now().plusMinutes(30));
        Token otherUserToken = createToken(inactiveUser, TokenType.PASSWORD_RESET,
                LocalDateTime.now().plusMinutes(30));

        resetPassword(resetBody(first.getTokenId().toString(), VALID_PASSWORD, VALID_PASSWORD))
                .andExpect(status().isNoContent());

        assertThat(resetTokensOf(activeUser)).isEmpty();
        assertThat(resetTokensOf(inactiveUser)).hasSize(1);
        assertThat(tokenRepository.findByTokenId(otherUserToken.getTokenId())).isPresent();
    }

    @Test
    @DisplayName("CP-034.16 - Token de un usuario DELETED: 422 y la contraseña sigue intacta")
    void resetPassword_deletedUser_returns422() throws Exception {
        Token token = createToken(deletedUser, TokenType.PASSWORD_RESET, LocalDateTime.now().plusMinutes(30));

        resetPassword(resetBody(token.getTokenId().toString(), VALID_PASSWORD, VALID_PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(INVALID_LINK_MESSAGE))
                .andExpect(jsonPath("$.detail").value(INVALID_LINK_DETAIL));

        assertThat(passwordEncoder.matches(OLD_PASSWORD, reloadUser(deletedUser).getPassword())).isTrue();
    }

    @Test
    @DisplayName("CP-034.17 - El token de confirmación sigue activando la cuenta")
    void confirmationToken_stillActivatesAccount() throws Exception {
        Token confirmationToken = tokenService.saveUserToken(inactiveUser);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", confirmationToken.getTokenId().toString());

        mockMvc.perform(post("/api/v1/users/" + inactiveUser.getUserId() + "/verify-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isAccepted());

        assertThat(reloadUser(inactiveUser).getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
}