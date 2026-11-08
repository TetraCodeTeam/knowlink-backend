package com.knowlink.api.tutors.controllers;

import com.knowlink.api.testsupport.CatalogFixtures;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Production runs with spring.jpa.open-in-view=false and without an ambient
 * transaction, so subjects coming out of a query are detached. Subject#isBasic()
 * reads the lazy career, which threw LazyInitializationException and turned both
 * public catalog endpoints into 500s whenever the result was not empty. This test
 * reproduces that condition: no class level transaction and no open session in view.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
class SubjectCatalogLazyLoadingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private IInstitutionRepository institutionRepository;
    @Autowired
    private ICareerRepository careerRepository;
    @Autowired
    private ISubjectRepository subjectRepository;

    private Institution institution;
    private Career career;
    private Career sharedCareer;
    private Subject subject;
    private Subject sharedSubject;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        institution = institutionRepository.save(CatalogFixtures.institution("UTN " + suffix));
        career = careerRepository.save(CatalogFixtures.career(institution, "Ingenieria " + suffix));
        sharedCareer = careerRepository.save(CatalogFixtures.sharedCareer(institution));
        subject = subjectRepository.save(CatalogFixtures.subject(career, "Programacion " + suffix));
        sharedSubject = subjectRepository.save(CatalogFixtures.subject(sharedCareer, "Analisis " + suffix));
    }

    @AfterEach
    void tearDown() {
        subjectRepository.delete(subject);
        subjectRepository.delete(sharedSubject);
        careerRepository.delete(career);
        careerRepository.delete(sharedCareer);
        institutionRepository.delete(institution);
    }

    @Test
    @DisplayName("GET /api/v1/subjects responde 200 aunque open-in-view este desactivado")
    void subjectsByCareer_returns200WhenOpenInViewIsDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/subjects").param("careerId", career.getCareerId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].subjectId").value(subject.getSubjectId().toString()))
                .andExpect(jsonPath("$[0].isBasic").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/subjects/basic responde 200 aunque open-in-view este desactivado")
    void basicSubjects_returns200WhenOpenInViewIsDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/subjects/basic")
                        .param("institutionId", institution.getInstitutionId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].subjectId").value(sharedSubject.getSubjectId().toString()))
                .andExpect(jsonPath("$[0].isBasic").value(true));
    }
}