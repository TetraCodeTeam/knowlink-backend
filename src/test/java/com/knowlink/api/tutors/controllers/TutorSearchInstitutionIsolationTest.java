package com.knowlink.api.tutors.controllers;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.testsupport.CatalogFixtures;
import com.knowlink.api.tutors.config.InstitutionCatalogData;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
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
class TutorSearchInstitutionIsolationTest {

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
    @Autowired
    private ITutorProfileRepository tutorProfileRepository;
    @Autowired
    private ITutorSubjectRepository tutorSubjectRepository;

    private Institution utn;
    private Institution unvm;
    private Career utnRegular;
    private Career unvmRegular;
    private Subject programacion;
    private Subject contabilidad;
    private User unvmStudent;
    private User utnTutor;
    private User unvmTutor;

    @BeforeEach
    void setUp() {
        utn = CatalogFixtures.saveInstitution(institutionRepository, InstitutionCatalogData.UTN_FRVM);
        unvm = CatalogFixtures.saveInstitution(institutionRepository, InstitutionCatalogData.UNVM);
        utnRegular = careerRepository.findByInstitution_InstitutionIdAndName(
                utn.getInstitutionId(), "Ingeniería en Sistemas").orElseThrow();
        unvmRegular = careerRepository.findByInstitution_InstitutionIdAndName(
                unvm.getInstitutionId(), "Licenciatura en Administración").orElseThrow();
        programacion = subjectRepository.findByInstitution_InstitutionIdAndName(
                utn.getInstitutionId(), "Programación").orElseThrow();
        contabilidad = subjectRepository.findByInstitution_InstitutionIdAndName(
                unvm.getInstitutionId(), "Contabilidad").orElseThrow();

        unvmStudent = userRepository.save(User.builder()
                .fullName("Alumno UNVM")
                .email("unvm.student." + UUID.randomUUID() + "@test.com")
                .password("hashed")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        studentProfileRepository.save(StudentProfile.builder()
                .user(unvmStudent)
                .career(unvmRegular)
                .build());

        utnTutor = tutor("Carlos UTN", utnRegular, programacion);
        unvmTutor = tutor("Maria UNVM", unvmRegular, contabilidad);
        tutorSubjectRepository.flush();
    }

    private User tutor(String fullName, Career career, Subject subject) {
        User user = userRepository.save(User.builder()
                .fullName(fullName)
                .email("tutor." + UUID.randomUUID() + "@test.com")
                .password("hashed")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        TutorProfile profile = tutorProfileRepository.save(TutorProfile.builder()
                .user(user)
                .career(career)
                .averageRating(4.5)
                .build());
        TutorSubject tutorSubject = TutorSubject.builder()
                .tutorProfile(profile)
                .subject(subject)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build();
        profile.getSubjects().add(tutorSubject);
        tutorSubjectRepository.save(tutorSubject);
        return user;
    }

    private RequestPostProcessor asStudent() {
        UserPrincipal principal = new UserPrincipal(unvmStudent);
        return authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("Un alumno de UNVM no ve tutores de UTN al buscar por materia")
    void search_bySubject_doesNotReturnTutorsFromAnotherInstitution() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/{query}", "Programación").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Un alumno de UNVM no ve tutores de UTN al buscar por nombre")
    void search_byTutorName_doesNotReturnTutorsFromAnotherInstitution() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/{query}", "Carlos UTN").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("La búsqueda sí devuelve los tutores de la institución del alumno")
    void search_returnsTutorsFromTheStudentInstitution() throws Exception {
        mockMvc.perform(get("/api/v1/tutors/search/{query}", "Maria UNVM").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Maria UNVM"));
    }

    @Test
    @DisplayName("La búsqueda por nombre de un tutor sin perfil de alumno devuelve 404")
    void search_withoutStudentProfile_returns404() throws Exception {
        User studentWithoutProfile = userRepository.save(User.builder()
                .fullName("Alumno Sin Perfil")
                .email("student.no.profile." + UUID.randomUUID() + "@test.com")
                .password("hashed")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        UserPrincipal principal = new UserPrincipal(studentWithoutProfile);
        RequestPostProcessor auth = authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        mockMvc.perform(get("/api/v1/tutors/search/{query}", "Contabilidad").with(auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("STUDENT_PROFILE_NOT_FOUND"));
    }
}
