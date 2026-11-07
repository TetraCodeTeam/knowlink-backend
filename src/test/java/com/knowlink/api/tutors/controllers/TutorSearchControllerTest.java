package com.knowlink.api.tutors.controllers;

import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.testsupport.CatalogFixtures;
import com.knowlink.api.tutors.config.InstitutionCatalogData;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
    private IInstitutionRepository institutionRepository;

    @Autowired
    private IStudentProfileRepository studentProfileRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ITutorProfileRepository tutorProfileRepository;

    @Autowired
    private ITutorSubjectRepository tutorSubjectRepository;

    private User studentUser;

    @BeforeEach
    void seedData() {
        Institution institution = CatalogFixtures.saveInstitution(
                institutionRepository, InstitutionCatalogData.UTN_FRVM);
        Career career = CatalogFixtures.saveCareer(careerRepository, institution, "Ingeniería en Sistemas");
        Career sharedCareer = CatalogFixtures.saveSharedCareer(careerRepository, institution);

        Subject algebra = CatalogFixtures.saveSubject(subjectRepository, sharedCareer, "Algebra");
        Subject fisica = CatalogFixtures.saveSubject(subjectRepository, sharedCareer, "Física");

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

        studentUser = userRepository.save(User.builder()
                .fullName("Ana Estudiante")
                .email("ana.student@test.com")
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
    @DisplayName("CP-003.01 - 200: devuelve los tutores que dictan la materia buscada")
    void search_returnsTutorsTeachingThatSubject_asStudent() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Ana García"))
                .andExpect(jsonPath("$[0].subjects[0].name").value("Algebra"))
                .andExpect(jsonPath("$[0].subjects[0].career").value("Materias Compartidas"));
    }

    @Test
    @DisplayName("CP-003.02 - 200: solo devuelve tutores por nombre completo, sin estudiantes")
    void search_matchesTutorsByFullName_onlyTutorsReturned() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Ana").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Ana García"));
    }

    @Test
    @DisplayName("CP-003.03 - 200: query sin coincidencias devuelve array vacio")
    void search_returnsEmptyArray_whenNothingMatches() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/zzz").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("CP-003.04 - 403: un tutor autenticado no puede buscar")
    void search_returns403_whenCalledByTutor() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra").with(asUser(Role.TUTOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CP-003.05 - 401: un usuario anonimo no esta autenticado")
    void search_returns401_whenCalledByAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/Algebra"))
                .andExpect(status().isUnauthorized());
    }
}
