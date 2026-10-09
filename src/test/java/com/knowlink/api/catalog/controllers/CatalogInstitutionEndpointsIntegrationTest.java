package com.knowlink.api.catalog.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogInstitutionEndpointsIntegrationTest {

    private static final String INSTITUTIONS = "/api/v1/catalog/institutions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private RequestPostProcessor asRole(Role role) {
        User user = User.builder()
                .fullName("Usuario Catalogo")
                .email("catalog.user." + UUID.randomUUID() + "@test.com")
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("CP-043.14 - Crear institucion genera automaticamente su carrera reservada COMPARTIDA")
    void createInstitution_autoCreatesSharedCareer() throws Exception {
        String name = "Institucion Test " + UUID.randomUUID();
        String responseBody = mockMvc.perform(post(INSTITUTIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}")
                        .with(asRole(Role.ADMIN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andReturn().getResponse().getContentAsString();

        JsonNode body = objectMapper.readTree(responseBody);
        String institutionId = body.get("institutionId").asText();

        mockMvc.perform(get(INSTITUTIONS + "/" + institutionId + "/careers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Materias Compartidas"))
                .andExpect(jsonPath("$[0].type").value("COMPARTIDA"));
    }

    @Test
    @DisplayName("CP-043.15 - POST de institucion sin autenticar responde 401")
    void createInstitution_anonymous_returns401() throws Exception {
        mockMvc.perform(post(INSTITUTIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anonima " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CP-043.16 - POST de institucion con rol no ADMIN responde 403")
    void createInstitution_studentRole_returns403() throws Exception {
        mockMvc.perform(post(INSTITUTIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"SinPermiso " + UUID.randomUUID() + "\"}")
                        .with(asRole(Role.STUDENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CP-043.17 - GET de instituciones es publico e incluye las sembradas")
    void getInstitutions_isPublicAndIncludesSeeded() throws Exception {
        mockMvc.perform(get(INSTITUTIONS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].name", hasItem("UTN FRVM")))
                .andExpect(jsonPath("$[*].name", hasItem("UNVM")));
    }

    @Test
    @DisplayName("CP-043.18 - Intentar crear una carrera COMPARTIDA manualmente responde 400 con mensaje exacto")
    void createCareer_sharedType_returns400WithExactMessage() throws Exception {
        String institutionId = mockMvc.perform(post(INSTITUTIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Institucion Carrera " + UUID.randomUUID() + "\"}")
                        .with(asRole(Role.ADMIN)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode body = objectMapper.readTree(institutionId);
        String id = body.get("institutionId").asText();

        mockMvc.perform(post(INSTITUTIONS + "/" + id + "/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Materias Compartidas\",\"type\":\"COMPARTIDA\"}")
                        .with(asRole(Role.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("No es posible crear carreras de tipo compartido manualmente"));
    }

    @Test
    @DisplayName("CP-043.19 - Crear carrera regular, listarla junto a la reservada y rechazar duplicado 409")
    void createCareer_regularListAndDuplicate() throws Exception {
        String institutionId = mockMvc.perform(post(INSTITUTIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Institucion Carreras " + UUID.randomUUID() + "\"}")
                        .with(asRole(Role.ADMIN)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode body = objectMapper.readTree(institutionId);
        String id = body.get("institutionId").asText();
        String careerName = "Carrera Regular " + UUID.randomUUID();

        mockMvc.perform(post(INSTITUTIONS + "/" + id + "/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + careerName + "\"}")
                        .with(asRole(Role.ADMIN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(careerName))
                .andExpect(jsonPath("$.type").value("REGULAR"));

        mockMvc.perform(get(INSTITUTIONS + "/" + id + "/careers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].type", hasItem("REGULAR")))
                .andExpect(jsonPath("$[*].type", hasItem("COMPARTIDA")));

        mockMvc.perform(post(INSTITUTIONS + "/" + id + "/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + careerName + "\"}")
                        .with(asRole(Role.ADMIN)))
                .andExpect(status().isConflict());
    }
}