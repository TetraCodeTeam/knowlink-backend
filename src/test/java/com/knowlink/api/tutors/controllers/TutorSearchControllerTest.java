package com.knowlink.api.tutors.controllers;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TutorSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ICareerRepository careerRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ITutorProfileRepository tutorProfileRepository;

    @Autowired
    private ITutorSubjectRepository tutorSubjectRepository;

    @BeforeEach
    void seedData() {
        Career career = careerRepository.findByName("Ingeniería en Sistemas")
                .orElseGet(() -> careerRepository.save(
                        Career.builder().name("Ingeniería en Sistemas").build()));

        Subject algebra = subjectRepository.findByName("Algebra")
                .orElseGet(() -> subjectRepository.save(
                        Subject.builder().name("Algebra").isBasic(true).career(career).build()));
        Subject fisica = subjectRepository.findByName("Física")
                .orElseGet(() -> subjectRepository.save(
                        Subject.builder().name("Física").isBasic(true).career(career).build()));

        User anaTutora = userRepository.save(User.builder()
                .fullName("Ana García")
                .email("ana.tutor@test.com")
                .password("hashed")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        TutorProfile perfilAna = tutorProfileRepository.save(TutorProfile.builder()
                .user(anaTutora)
                .career(career)
                .averageRating(4.5)
                .build());
        TutorSubject tutorSubjectAlgebra = TutorSubject.builder()
                .tutorProfile(perfilAna)
                .subject(algebra)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build();
        perfilAna.getSubjects().add(tutorSubjectAlgebra);
        tutorSubjectRepository.save(tutorSubjectAlgebra);

        User carlosTutor = userRepository.save(User.builder()
                .fullName("Carlos López")
                .email("carlos.tutor@test.com")
                .password("hashed")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        TutorProfile perfilCarlos = tutorProfileRepository.save(TutorProfile.builder()
                .user(carlosTutor)
                .career(career)
                .averageRating(4.0)
                .build());
        TutorSubject tutorSubjectFisica = TutorSubject.builder()
                .tutorProfile(perfilCarlos)
                .subject(fisica)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.IN_PERSON)
                .build();
        perfilCarlos.getSubjects().add(tutorSubjectFisica);
        tutorSubjectRepository.save(tutorSubjectFisica);

        userRepository.save(User.builder()
                .fullName("Ana Estudiante")
                .email("ana.student@test.com")
                .password("hashed")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private RequestPostProcessor asUser(Role role) {
        User user = User.builder()
                .fullName("Usuario Autenticado")
                .email("auth@test.com")
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void search_returnsTutorsTeachingThatSubject_asStudent() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra").with(asUser(Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Ana García"))
                .andExpect(jsonPath("$[0].subjects[0].name").value("Algebra"))
                .andExpect(jsonPath("$[0].subjects[0].career").value("Ingeniería en Sistemas"));
    }

    @Test
    void search_matchesTutorsByFullName_onlyTutorsReturned() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Ana").with(asUser(Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Ana García"));
    }

    @Test
    void search_returnsEmptyArray_whenNothingMatches() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/zzz").with(asUser(Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void search_returns403_whenCalledByTutor() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra").with(asUser(Role.TUTOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void search_returns401_whenCalledByAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra"))
                .andExpect(status().isUnauthorized());
    }
}
