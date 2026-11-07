package com.knowlink.api.tutors.controllers;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.testsupport.CatalogFixtures;
import com.knowlink.api.tutors.config.InstitutionCatalogData;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogControllerIntegrationTest {

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

    private Institution utn;
    private Institution unvm;
    private Career utnRegular;
    private Career unvmRegular;

    @BeforeEach
    void setUp() {
        utn = CatalogFixtures.saveInstitution(institutionRepository, InstitutionCatalogData.UTN_FRVM);
        unvm = CatalogFixtures.saveInstitution(institutionRepository, InstitutionCatalogData.UNVM);
        utnRegular = careerRepository.findByInstitution_InstitutionIdAndName(
                utn.getInstitutionId(), "Ingeniería en Sistemas").orElseThrow();
        unvmRegular = careerRepository.findByInstitution_InstitutionIdAndName(
                unvm.getInstitutionId(), "Licenciatura en Administración").orElseThrow();
    }

    private RequestPostProcessor as(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private User studentWithProfile(Institution institution, Career career) {
        User student = userRepository.save(User.builder()
                .fullName("Student " + UUID.randomUUID())
                .email("student." + UUID.randomUUID() + "@test.com")
                .password("hashed")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        studentProfileRepository.save(StudentProfile.builder()
                .user(student)
                .career(career)
                .build());
        return student;
    }

    @Test
    @DisplayName("GET /institutions es público y lista el catálogo completo")
    void institutions_isPublicAndListsEveryInstitution() throws Exception {
        mockMvc.perform(get("/api/v1/institutions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem(InstitutionCatalogData.UTN_FRVM)))
                .andExpect(jsonPath("$[*].name", hasItem(InstitutionCatalogData.UNVM)));
    }

    @Test
    @DisplayName("GET /careers devuelve solo carreras regulares y nunca la compartida")
    void careers_excludesSharedCareer() throws Exception {
        mockMvc.perform(get("/api/v1/careers").param("institutionId", utn.getInstitutionId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Ingeniería en Sistemas")))
                .andExpect(jsonPath("$[*].name", not(hasItem(InstitutionCatalogData.SHARED_CAREER_NAME))));
    }

    @Test
    @DisplayName("GET /careers exige institutionId")
    void careers_withoutInstitutionId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/careers"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /careers con una institución inexistente devuelve 404")
    void careers_unknownInstitution_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/careers").param("institutionId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("INSTITUTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /subjects/basic devuelve las materias compartidas de la institución")
    void basicSubjects_returnsSharedSubjectsOfTheInstitution() throws Exception {
        mockMvc.perform(get("/api/v1/subjects/basic").param("institutionId", utn.getInstitutionId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(org.hamcrest.Matchers.greaterThan(0))))
                .andExpect(jsonPath("$[*].name", hasItem("Análisis Matemático")))
                .andExpect(jsonPath("$[*].isBasic", everyItem(is(true))));
    }

    @Test
    @DisplayName("GET /subjects devuelve solo las materias propias de la carrera")
    void subjectsByCareer_returnsOnlyOwnSubjects() throws Exception {
        List<Subject> expected = subjectRepository.findByCareer_CareerIdOrderByName(utnRegular.getCareerId());
        mockMvc.perform(get("/api/v1/subjects").param("careerId", utnRegular.getCareerId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(expected.size())))
                .andExpect(jsonPath("$[*].isBasic", everyItem(is(false))));
    }

    @Test
    @DisplayName("GET /community/careers se acota a la institución del alumno")
    void communityCareers_isScopedToStudentInstitution() throws Exception {
        User student = studentWithProfile(unvm, unvmRegular);

        mockMvc.perform(get("/api/v1/community/careers").with(as(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Licenciatura en Administración")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Ingeniería Civil"))))
                .andExpect(jsonPath("$[*].name", not(hasItem(InstitutionCatalogData.SHARED_CAREER_NAME))));
    }

    @Test
    @DisplayName("GET /community/subjects sin careerId se acota a la institución del alumno")
    void communitySubjectsWithoutCareer_isScopedToStudentInstitution() throws Exception {
        String unvmSubjectName = "Materia UNVM " + UUID.randomUUID();
        String utnSubjectName = "Materia UTN " + UUID.randomUUID();
        subjectRepository.save(CatalogFixtures.subject(unvmRegular, unvmSubjectName));
        subjectRepository.save(CatalogFixtures.subject(utnRegular, utnSubjectName));
        User student = studentWithProfile(unvm, unvmRegular);

        mockMvc.perform(get("/api/v1/community/subjects").with(as(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem(unvmSubjectName)))
                .andExpect(jsonPath("$[*].name", not(hasItem(utnSubjectName))));
    }

    @Test
    @DisplayName("GET /community/subjects?careerId incluye las compartidas solo de la misma institución")
    void communitySubjectsByCareer_includesOnlySameInstitutionSharedSubjects() throws Exception {
        User student = studentWithProfile(unvm, unvmRegular);

        mockMvc.perform(get("/api/v1/community/subjects")
                        .param("careerId", unvmRegular.getCareerId().toString())
                        .with(as(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Análisis Matemático")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Programación"))));
    }
}
