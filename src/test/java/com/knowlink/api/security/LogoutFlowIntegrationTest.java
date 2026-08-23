package com.knowlink.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LogoutFlowIntegrationTest {

    private static final String TEST_JWT_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1rbm93bGluay10ZXN0aW5nLXB1cnBvc2VzLW9ubHk=";
    private static final String STUDENT_EMAIL = "student.logout@test.com";
    private static final String STUDENT_PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userId;

    @BeforeEach
    void setUp() {
        User student = User.builder()
                .fullName("Estudiante Logout")
                .email(STUDENT_EMAIL)
                .password(passwordEncoder.encode(STUDENT_PASSWORD))
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        userId = userRepository.save(student).getUserId();
    }

    private String loginAndGetToken() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + STUDENT_EMAIL + "\",\"password\":\"" + STUDENT_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String expiredToken(String subject) {
        Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_JWT_SECRET));
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis() - 60_000))
                .setExpiration(new Date(System.currentTimeMillis() - 30_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    @DisplayName("AC-1 - logout invalida el token: un request posterior con el mismo token responde 401")
    void logout_invalidatesTokenForSubsequentRequests() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(get("/api/v1/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC-2 - tras logout, cualquier endpoint protegido responde 401 con el token invalidado")
    void protectedEndpoint_rejectsBlacklistedToken() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Edge - logout con token ya expirado responde 401, no 500")
    void logout_withExpiredToken_returns401() throws Exception {
        String token = expiredToken(STUDENT_EMAIL);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Edge - logout sin header Authorization responde 401")
    void logout_withoutAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
    }
}