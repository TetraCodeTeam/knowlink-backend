package com.knowlink.api.catalog.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.catalog.data.enums.CareerType;
import com.knowlink.api.catalog.data.models.Institution;
import com.knowlink.api.catalog.repositories.IInstitutionRepository;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.BeforeEach;
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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogSubjectEndpointsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IInstitutionRepository institutionRepository;

    @Autowired
    private ICareerRepository careerRepository;

    private Institution utn;
    private Institution unvm;
    private Career sistemas;
    private Career unvmCareer;
    private Career sharedUtn;

    @BeforeEach
    void setUpCatalog() {
        utn = institutionRepository.findByName("UTN FRVM").orElseThrow();
        unvm = institutionRepository.findByName("UNVM").orElseThrow();
        sistemas = careerRepository.findByName("Ingeniería en Sistemas").orElseThrow();
        unvmCareer = careerRepository.findByName("Profesorado de Matemática").orElseThrow();
        sharedUtn = careerRepository
                .findByInstitutionInstitutionIdAndType(utn.getInstitutionId(), CareerType.COMPARTIDA)
                .stream().findFirst().orElseThrow();
    }

    private RequestPostProcessor asRole(Role role) {
        User user = User.builder()
                .fullName("Usuario Materias")
                .email("subject.user." + UUID.randomUUID() + "@test.com")
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private JsonNode createSubject(UUID institutionId, String name, UUID... careerIds) throws Exception {
        StringBuilder careers = new StringBuilder();
        for (UUID careerId : careerIds) {
            if (careers.length() > 0) {
                careers.append(",");
            }
            careers.append("\"").append(careerId).append("\"");
        }
        String body = mockMvc.perform(post("/api/v1/catalog/institutions/" + institutionId + "/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"careerIds\":[" + careers + "]}")
                        .with(asRole(Role.ADMIN)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    @DisplayName("CP-043.20 - Crear materia asociada a una carrera de la institucion devuelve 201")
    void createSubject_returns201WithCareerIds() throws Exception {
        String name = "Materia Nueva " + UUID.randomUUID();

        JsonNode response = createSubject(utn.getInstitutionId(), name, sistemas.getCareerId());

        org.assertj.core.api.Assertions.assertThat(response.get("name").asText()).isEqualTo(name);
        org.assertj.core.api.Assertions.assertThat(response.get("isBasic").asBoolean()).isFalse();
        org.assertj.core.api.Assertions.assertThat(response.get("careerIds").toString())
                .contains(sistemas.getCareerId().toString());
    }

    @Test
    @DisplayName("CP-043.21 - Materia duplicada en la misma institucion responde 409 con contrato exacto")
    void createSubject_duplicate_returns409Contract() throws Exception {
        String name = "Materia Duplicada " + UUID.randomUUID();
        JsonNode created = createSubject(utn.getInstitutionId(), name, sistemas.getCareerId());
        String existingId = created.get("subjectId").asText();

        mockMvc.perform(post("/api/v1/catalog/institutions/" + utn.getInstitutionId() + "/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"careerIds\":[\"" + sharedUtn.getCareerId() + "\"]}")
                        .with(asRole(Role.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("MATERIA_DUPLICADA"))
                .andExpect(jsonPath("$.message").value(
                        "Ya existe una materia con ese nombre en esta institución. ¿Querés agregarle esta carrera?"))
                .andExpect(jsonPath("$.materiaExistenteId").value(existingId));
    }

    @Test
    @DisplayName("CP-043.22 - La misma materia puede existir con ese nombre en otra institucion")
    void createSubject_sameNameInAnotherInstitution_allowed() throws Exception {
        String name = "Materia Compartida Nombre " + UUID.randomUUID();
        createSubject(utn.getInstitutionId(), name, sistemas.getCareerId());

        JsonNode other = createSubject(unvm.getInstitutionId(), name, unvmCareer.getCareerId());

        org.assertj.core.api.Assertions.assertThat(other.get("subjectId").asText())
                .isNotEqualTo("");
    }

    @Test
    @DisplayName("CP-043.23 - Asociar materias a carreras de otra institucion responde 400")
    void createSubject_foreignCareer_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/institutions/" + utn.getInstitutionId() + "/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Materia Ajena " + UUID.randomUUID()
                                + "\",\"careerIds\":[\"" + unvmCareer.getCareerId() + "\"]}")
                        .with(asRole(Role.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Solo podés asociar materias a carreras de la misma institución."));
    }

    @Test
    @DisplayName("CP-043.24 - PATCH REPLACE reemplaza asociaciones sin modificar el nombre")
    void updateSubjectCareers_replaceKeepsName() throws Exception {
        String name = "Materia A Reemplazar " + UUID.randomUUID();
        JsonNode created = createSubject(utn.getInstitutionId(), name, sistemas.getCareerId());
        String subjectId = created.get("subjectId").asText();

        mockMvc.perform(patch("/api/v1/catalog/subjects/" + subjectId + "/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careerIds\":[\"" + sharedUtn.getCareerId() + "\"],\"mode\":\"REPLACE\"}")
                        .with(asRole(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.careerIds", hasSize(1)))
                .andExpect(jsonPath("$.careerIds[0]").value(sharedUtn.getCareerId().toString()));
    }

    @Test
    @DisplayName("CP-043.25 - PATCH ADD agrega la carrera reservada manteniendo la asociacion original")
    void updateSubjectCareers_addKeepsExisting() throws Exception {
        String name = "Materia A Ampliar " + UUID.randomUUID();
        JsonNode created = createSubject(utn.getInstitutionId(), name, sistemas.getCareerId());
        String subjectId = created.get("subjectId").asText();

        mockMvc.perform(patch("/api/v1/catalog/subjects/" + subjectId + "/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careerIds\":[\"" + sharedUtn.getCareerId() + "\"],\"mode\":\"ADD\"}")
                        .with(asRole(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.careerIds", hasSize(2)))
                .andExpect(jsonPath("$.careerIds", containsInAnyOrder(sistemas.getCareerId().toString(),
                        sharedUtn.getCareerId().toString())));
    }

    @Test
    @DisplayName("CP-043.26 - Materias visibles para una carrera incluyen las suyas y las compartidas, no las de otra institucion")
    void getSubjects_visibleForCareerExcludesOtherInstitution() throws Exception {
        String utnSubject = "Materia Visible " + UUID.randomUUID();
        createSubject(utn.getInstitutionId(), utnSubject, sistemas.getCareerId());
        String unvmSubject = "Materia Del Otro Lado " + UUID.randomUUID();
        createSubject(unvm.getInstitutionId(), unvmSubject, unvmCareer.getCareerId());

        mockMvc.perform(get("/api/v1/catalog/subjects")
                        .param("institutionId", utn.getInstitutionId().toString())
                        .param("careerId", sistemas.getCareerId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem(utnSubject)))
                .andExpect(jsonPath("$[*].name", hasItem("Programación")))
                .andExpect(jsonPath("$[*].name", hasItem("Análisis Matemático")))
                .andExpect(jsonPath("$[*].name", not(hasItem(unvmSubject))))
                .andExpect(jsonPath("$[*].name", not(hasItem("Programación Web"))));
    }

    @Test
    @DisplayName("CP-043.27 - GET de materias sin institutionId responde 400")
    void getSubjects_missingInstitutionId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/subjects"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-043.28 - POST de materia sin autenticar responde 401")
    void createSubject_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/institutions/" + utn.getInstitutionId() + "/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anonima " + UUID.randomUUID()
                                + "\",\"careerIds\":[\"" + sistemas.getCareerId() + "\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CP-043.29 - POST de materia con rol no ADMIN responde 403")
    void createSubject_studentRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/institutions/" + utn.getInstitutionId() + "/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"SinPermiso " + UUID.randomUUID()
                                + "\",\"careerIds\":[\"" + sistemas.getCareerId() + "\"]}")
                        .with(asRole(Role.STUDENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CP-043.30 - GET de materias es publico (alimenta el selector de registro)")
    void getSubjects_isPublic() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/subjects")
                        .param("institutionId", utn.getInstitutionId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }
}