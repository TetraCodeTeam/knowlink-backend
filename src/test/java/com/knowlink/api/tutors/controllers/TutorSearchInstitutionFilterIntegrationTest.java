package com.knowlink.api.tutors.controllers;

import com.knowlink.api.catalog.data.models.Institution;
import com.knowlink.api.catalog.repositories.IInstitutionRepository;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
class TutorSearchInstitutionFilterIntegrationTest {

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
    private ITutorProfileRepository tutorProfileRepository;

    @Autowired
    private ITutorSubjectRepository tutorSubjectRepository;

    @Autowired
    private IStudentProfileRepository studentProfileRepository;

    private String queryToken;
    private User utnStudent;
    private User unvmStudent;
    private User plainUser;

    @BeforeEach
    void setUpInstitutions() {
        Institution utn = institutionRepository.findByName("UTN FRVM").orElseThrow();
        Institution unvm = institutionRepository.findByName("UNVM").orElseThrow();
        Career sistemas = careerRepository.findByName("Ingeniería en Sistemas").orElseThrow();
        Career unvmCareer = careerRepository.findByName("Profesorado de Matemática").orElseThrow();
        queryToken = "FiltroInstitucion" + UUID.randomUUID().toString().replace("-", "");

        Subject utnSubject = subjectRepository.save(Subject.builder()
                .name(queryToken)
                .isBasic(false)
                .institution(utn)
                .careers(java.util.Set.of(sistemas))
                .build());
        Subject unvmSubject = subjectRepository.save(Subject.builder()
                .name(queryToken)
                .isBasic(false)
                .institution(unvm)
                .careers(java.util.Set.of(unvmCareer))
                .build());

        saveTutor("Tutor UTN " + queryToken, sistemas, utnSubject);
        saveTutor("Tutor UNVM " + queryToken, unvmCareer, unvmSubject);

        utnStudent = saveUser("Alumno UTN", "alumno.utn." + UUID.randomUUID() + "@test.com", Role.STUDENT);
        studentProfileRepository.save(StudentProfile.builder()
                .user(utnStudent)
                .career(sistemas)
                .createdAt(LocalDateTime.now())
                .build());

        unvmStudent = saveUser("Alumno UNVM", "alumno.unvm." + UUID.randomUUID() + "@test.com", Role.STUDENT);
        studentProfileRepository.save(StudentProfile.builder()
                .user(unvmStudent)
                .career(unvmCareer)
                .createdAt(LocalDateTime.now())
                .build());

        plainUser = saveUser("Sin Perfil", "sin.perfil." + UUID.randomUUID() + "@test.com", Role.STUDENT);
    }

    private User saveUser(String fullName, String email, Role role) {
        return userRepository.save(User.builder()
                .fullName(fullName)
                .email(email)
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private void saveTutor(String fullName, Career career, Subject subject) {
        User tutorUser = saveUser(fullName, fullName.replace(" ", ".").toLowerCase() + "." + UUID.randomUUID()
                + "@test.com", Role.TUTOR);
        TutorProfile profile = tutorProfileRepository.save(TutorProfile.builder()
                .user(tutorUser)
                .career(career)
                .verified(true)
                .build());
        tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(profile)
                .subject(subject)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .pricePerHour(new java.math.BigDecimal("2000.00"))
                .build());
    }

    private RequestPostProcessor asSavedUser(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("CP-043.38 - El alumno de UTN busca y solo ve tutores de su institucion (US-06)")
    void search_asUtnStudent_onlyResultsFromOwnInstitution() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/" + queryToken)
                        .with(asSavedUser(utnStudent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Tutor UTN " + queryToken));
    }

    @Test
    @DisplayName("CP-043.39 - El alumno de UNVM busca y solo ve tutores de su institucion (US-06)")
    void search_asUnvmStudent_onlyResultsFromOwnInstitution() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/" + queryToken)
                        .with(asSavedUser(unvmStudent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Tutor UNVM " + queryToken));
    }

    @Test
    @DisplayName("CP-043.40 - Un usuario sin perfil de alumno no filtra por institucion")
    void search_asUserWithoutStudentProfile_noInstitutionFilter() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/" + queryToken)
                        .with(asSavedUser(plainUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}