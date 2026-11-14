package com.knowlink.api.tutors.controllers;

import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.testsupport.CatalogFixtures;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-06 QA: GET /api/v1/subjects/search/{query} exposes the catalog subjects of
 * the student institution even when no tutor teaches them. The catalog is
 * seeded under a unique institution so the seed data of the application context
 * cannot leak into the assertions, and no tutor is created at all: every
 * subject returned here has zero tutors.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SubjectSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IInstitutionRepository institutionRepository;

    @Autowired
    private ICareerRepository careerRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IStudentProfileRepository studentProfileRepository;

    private User studentUser;

    @BeforeEach
    void seedData() {
        String suffix = UUID.randomUUID().toString();
        Institution institution = institutionRepository.save(
                CatalogFixtures.institution("UTN US06 " + suffix));
        Institution otherInstitution = institutionRepository.save(
                CatalogFixtures.institution("UNVM US06 " + suffix));
        Career career = careerRepository.save(CatalogFixtures.career(institution, "Ingeniería en Sistemas"));
        Career otherCareer = careerRepository.save(
                CatalogFixtures.career(otherInstitution, "Licenciatura en Administración"));

        subjectRepository.save(CatalogFixtures.subject(career, "Álgebra"));
        subjectRepository.save(CatalogFixtures.subject(career, "Análisis"));
        subjectRepository.save(CatalogFixtures.subject(career, "Base de Datos"));
        subjectRepository.save(CatalogFixtures.subject(career, "Calculo"));
        // Same subject name in another institution: it must never show up here.
        subjectRepository.save(CatalogFixtures.subject(otherCareer, "Álgebra"));

        studentUser = userRepository.save(User.builder()
                .fullName("Alumno US06")
                .email("student.us06." + suffix + "@test.com")
                .password("hashed")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        studentProfileRepository.save(StudentProfile.builder()
                .user(studentUser)
                .career(career)
                .build());
    }

    private RequestPostProcessor asStudent() {
        UserPrincipal principal = new UserPrincipal(studentUser);
        return authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private RequestPostProcessor asUser(Role role) {
        User user = User.builder()
                .fullName("Usuario Autenticado")
                .email("auth.us06." + UUID.randomUUID() + "@test.com")
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("CP-US06-01 - 200: devuelve la materia del catálogo aunque ningún tutor la dicta")
    void search_returnsCatalogSubjectWithoutTutors_asStudent() throws Exception {
        mockMvc.perform(get("/api/v1/subjects/search/{query}", "álgebra").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Álgebra"))
                .andExpect(jsonPath("$[0].career").value("Ingeniería en Sistemas"));
    }

    @Test
    @DisplayName("CP-US06-02 - 200: la búsqueda de materias no distingue mayúsculas")
    void search_isCaseInsensitive() throws Exception {
        mockMvc.perform(get("/api/v1/subjects/search/{query}", "CALCULO").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Calculo"));
    }

    @Test
    @DisplayName("CP-US06-03 - 200: orden alfabético con prioridad a las que empiezan con el texto")
    void search_ordersWithPrefixPriorityThenAlphabetically() throws Exception {
        mockMvc.perform(get("/api/v1/subjects/search/{query}", "a").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].name", contains(
                        "Álgebra", "Análisis", "Base de Datos", "Calculo")));
    }

    @Test
    @DisplayName("CP-US06-04 - 200: la búsqueda se limita a la institución del alumno")
    void search_doesNotReturnSubjectsFromAnotherInstitution() throws Exception {
        // The other institution has its own "Álgebra", which also matches "a".
        mockMvc.perform(get("/api/v1/subjects/search/{query}", "a").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].career", contains(
                        "Ingeniería en Sistemas", "Ingeniería en Sistemas",
                        "Ingeniería en Sistemas", "Ingeniería en Sistemas")));
    }

    @Test
    @DisplayName("CP-US06-05 - 200: query sin coincidencias devuelve array vacio")
    void search_returnsEmptyArray_whenNothingMatches() throws Exception {
        mockMvc.perform(get("/api/v1/subjects/search/{query}", "zzz").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("CP-US06-06 - 403: un tutor autenticado no puede buscar materias")
    void search_returns403_whenCalledByTutor() throws Exception {
        mockMvc.perform(get("/api/v1/subjects/search/{query}", "álgebra").with(asUser(Role.TUTOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CP-US06-07 - 401: un usuario anonimo no esta autenticado")
    void search_returns401_whenCalledByAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/subjects/search/{query}", "álgebra"))
                .andExpect(status().isUnauthorized());
    }
}