package com.knowlink.api.tutors.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TutorProfileUpdateControllerTest {

    private static final String URL = "/api/v1/tutors/me/profile";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ICareerRepository careerRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private ITutorProfileRepository tutorProfileRepository;

    @Autowired
    private IStudentProfileRepository studentProfileRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private Career careerA;
    private Career careerB;
    private User tutorUser;
    private TutorProfile tutorProfile;
    private TutorSubject virtualSubject;
    private User otherUser;
    private User noSubjectsTutor;
    private TutorProfile noSubjectsProfile;

    @BeforeEach
    void seedData() {
        careerA = careerRepository.save(Career.builder().name("Ingeniería en Sistemas Tutor US-03").build());
        careerB = careerRepository.save(Career.builder().name("Medicina Tutor US-03").build());

        Subject algebra = subjectRepository.save(Subject.builder()
                .name("Algebra Tutor US-03")
                .isBasic(true)
                .career(careerA)
                .build());

        tutorUser = userRepository.save(User.builder()
                .firstName("Carlos")
                .lastName("López")
                .fullName("Carlos López")
                .email("tutor.edit.profile@test.com")
                .password("hashed")
                .dni("30222333")
                .phoneNumber("+54 9 3534222222")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        tutorProfile = TutorProfile.builder()
                .user(tutorUser)
                .career(careerA)
                .institutionalId("22222")
                .biography("Biografía inicial")
                .address("Calle Vieja 123")
                .mercadoPagoLinked(true)
                .profilePictureUrl("https://cdn.test/tutor.png")
                .build();

        virtualSubject = TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(algebra)
                .compensationType(CompensationType.FREE)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build();
        tutorProfile.getSubjects().add(virtualSubject);
        tutorProfile = tutorProfileRepository.save(tutorProfile);

        otherUser = userRepository.save(User.builder()
                .fullName("Otro Tutor US-03")
                .email("otro.tutor.edit.profile@test.com")
                .password("hashed")
                .dni("30999777")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        noSubjectsTutor = userRepository.save(User.builder()
                .fullName("Sin Materias US-03")
                .email("sin.materias.edit.profile@test.com")
                .password("hashed")
                .dni("30555666")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        noSubjectsProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(noSubjectsTutor)
                .career(careerA)
                .build());
    }

    private RequestPostProcessor asUser(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private RequestPostProcessor asStudent() {
        User student = User.builder()
                .fullName("Alumno Autenticado")
                .email("alumno.auth.tutor.edit.profile@test.com")
                .password("hashed")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        return asUser(student);
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("firstName", "Carlos");
        payload.put("lastName", "López");
        payload.put("dni", "30222333");
        payload.put("phoneNumber", "+54 9 3534222222");
        payload.put("career", careerA.getName());
        payload.put("institutionalId", "22222");
        payload.put("biography", "Biografía inicial");
        payload.put("address", "Calle Vieja 123");
        return payload;
    }

    private String json(Map<String, Object> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private ResultActions putProfile(Map<String, Object> payload, RequestPostProcessor auth) throws Exception {
        return mockMvc.perform(put(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(payload))
                .with(auth));
    }

    private ResultActions putProfile(Map<String, Object> payload) throws Exception {
        return putProfile(payload, asUser(tutorUser));
    }

    private TutorProfile reloadProfile(UUID userId) {
        entityManager.flush();
        entityManager.clear();
        return tutorProfileRepository.findByUserId(userId).orElseThrow();
    }

    private User reloadUser(UUID userId) {
        entityManager.flush();
        entityManager.clear();
        return userRepository.findById(userId).orElseThrow();
    }

    private TutorSubject buildSubject(String name, Modality modality) {
        Subject subject = subjectRepository.save(Subject.builder()
                .name(name)
                .isBasic(true)
                .career(careerA)
                .build());
        return TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .compensationType(CompensationType.FREE)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(modality)
                .build();
    }

    @Test
    @DisplayName("CP-003.11 - 200: el tutor actualiza datos comunes, biografía y dirección")
    void updateProfile_updatesCommonFields() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("firstName", "Carlos Alberto");
        payload.put("lastName", "Gómez");
        payload.put("dni", "40111222");
        payload.put("phoneNumber", "+54 9 3534999999");
        payload.put("institutionalId", "33333");
        payload.put("biography", "Nueva biografía");
        payload.put("address", "Nueva dirección 456");

        putProfile(payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Carlos Alberto Gómez"))
                .andExpect(jsonPath("$.firstName").value("Carlos Alberto"))
                .andExpect(jsonPath("$.lastName").value("Gómez"))
                .andExpect(jsonPath("$.dni").value("40111222"))
                .andExpect(jsonPath("$.phoneNumber").value("+54 9 3534999999"))
                .andExpect(jsonPath("$.career").value(careerA.getName()))
                .andExpect(jsonPath("$.institutionalId").value("33333"))
                .andExpect(jsonPath("$.biography").value("Nueva biografía"))
                .andExpect(jsonPath("$.address").value("Nueva dirección 456"));

        mockMvc.perform(get(URL).with(asUser(tutorUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Carlos Alberto Gómez"))
                .andExpect(jsonPath("$.firstName").value("Carlos Alberto"))
                .andExpect(jsonPath("$.lastName").value("Gómez"))
                .andExpect(jsonPath("$.dni").value("40111222"))
                .andExpect(jsonPath("$.phoneNumber").value("+54 9 3534999999"))
                .andExpect(jsonPath("$.biography").value("Nueva biografía"))
                .andExpect(jsonPath("$.address").value("Nueva dirección 456"));

        User reloaded = reloadUser(tutorUser.getUserId());
        assertThat(reloaded.getFullName()).isEqualTo("Carlos Alberto Gómez");
        assertThat(reloaded.getEmail()).isEqualTo("tutor.edit.profile@test.com");

        TutorProfile reloadedProfile = reloadProfile(tutorUser.getUserId());
        assertThat(reloadedProfile.getBiography()).isEqualTo("Nueva biografía");
        assertThat(reloadedProfile.getAddress()).isEqualTo("Nueva dirección 456");
        assertThat(reloadedProfile.getInstitutionalId()).isEqualTo("33333");
    }

    @Test
    @DisplayName("CP-003.12 - 400 con biografía de 301 caracteres y 200 con 300")
    void updateProfile_biographyLengthLimit() throws Exception {
        Map<String, Object> tooLong = validPayload();
        tooLong.put("biography", "a".repeat(301));
        putProfile(tooLong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));

        Map<String, Object> limit = validPayload();
        limit.put("biography", "a".repeat(300));
        putProfile(limit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biography").value("a".repeat(300)));
    }

    @Test
    @DisplayName("CP-003.13 - 200: dirección en blanco con solo materias virtuales queda null")
    void updateProfile_blankAddressWithVirtualSubject() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("address", "   ");

        putProfile(payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").isEmpty());

        assertThat(reloadProfile(tutorUser.getUserId()).getAddress()).isNull();
    }

    @Test
    @DisplayName("CP-003.14 - 400: dirección obligatoria con materias presenciales o mixtas")
    void updateProfile_addressRequiredForInPersonSubjects() throws Exception {
        tutorProfile.getSubjects().add(buildSubject("Física Tutor US-03", Modality.IN_PERSON));
        tutorProfile.getSubjects().add(buildSubject("Química Tutor US-03", Modality.BOTH));
        tutorProfileRepository.save(tutorProfile);
        entityManager.flush();

        Map<String, Object> blank = validPayload();
        blank.put("firstName", "Nombre Nuevo");
        blank.put("address", "   ");
        putProfile(blank)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));

        Map<String, Object> missing = validPayload();
        missing.put("firstName", "Nombre Nuevo");
        missing.remove("address");
        putProfile(missing)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));

        TutorProfile unchanged = reloadProfile(tutorUser.getUserId());
        assertThat(unchanged.getAddress()).isEqualTo("Calle Vieja 123");
        assertThat(unchanged.getUser().getFullName()).isEqualTo("Carlos López");
    }

    @Test
    @DisplayName("CP-003.15 - 400: no puede cambiar de carrera teniendo materias")
    void updateProfile_cannotChangeCareerWithSubjects() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("firstName", "Nombre Nuevo");
        payload.put("career", careerB.getName());

        putProfile(payload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));

        TutorProfile unchanged = reloadProfile(tutorUser.getUserId());
        assertThat(unchanged.getCareer().getName()).isEqualTo(careerA.getName());
        assertThat(unchanged.getUser().getFullName()).isEqualTo("Carlos López");
    }

    @Test
    @DisplayName("CP-003.16 - 200: puede cambiar de carrera si no tiene materias")
    void updateProfile_canChangeCareerWithoutSubjects() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("career", careerB.getName());
        payload.put("dni", "30555666");
        payload.put("firstName", "Tutor Sin Materias");

        putProfile(payload, asUser(noSubjectsTutor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.career").value(careerB.getName()))
                .andExpect(jsonPath("$.firstName").value("Tutor Sin Materias"));

        assertThat(reloadProfile(noSubjectsTutor.getUserId()).getCareer().getName())
                .isEqualTo(careerB.getName());
    }

    @Test
    @DisplayName("CP-003.17 - 409 DUPLICATE_DNI: el DNI de otro usuario no se puede guardar")
    void updateProfile_duplicateDniOfAnotherUser() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("dni", otherUser.getDni());

        putProfile(payload)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("DUPLICATE_DNI"))
                .andExpect(jsonPath("$.message").value("Este DNI ya está registrado."));

        assertThat(reloadUser(tutorUser.getUserId()).getDni()).isEqualTo("30222333");
    }

    @Test
    @DisplayName("CP-003.18 - 403: un estudiante no puede editar el perfil de tutor")
    void updateProfile_forbiddenForStudent() throws Exception {
        putProfile(validPayload(), asStudent())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CP-003.19 - rol dual: los datos comunes se reflejan en el perfil de alumno")
    void updateProfile_dualRoleSharesUserData() throws Exception {
        User dualUser = userRepository.save(User.builder()
                .firstName("Lucía")
                .lastName("Dual")
                .fullName("Lucía Dual")
                .email("dual.edit.profile@test.com")
                .password("hashed")
                .dni("30444555")
                .phoneNumber("+54 9 3534111111")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        TutorProfile dualTutorProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(dualUser)
                .career(careerA)
                .build());

        studentProfileRepository.save(StudentProfile.builder()
                .user(dualUser)
                .career(careerA)
                .build());

        Map<String, Object> payload = validPayload();
        payload.put("firstName", "Lucía Sofía");
        payload.put("lastName", "Dual Gómez");
        payload.put("dni", "30444555");
        payload.put("phoneNumber", "+54 9 3534777777");
        payload.put("career", careerA.getName());
        payload.put("institutionalId", "44444");
        payload.put("biography", "Bio dual");
        payload.put("address", "Calle Dual 789");

        putProfile(payload, asUser(dualUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Lucía Sofía Dual Gómez"));

        dualUser.setRole(Role.STUDENT);
        userRepository.save(dualUser);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/students/me/profile").with(asUser(dualUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Lucía Sofía Dual Gómez"))
                .andExpect(jsonPath("$.phoneNumber").value("+54 9 3534777777"));

        assertThat(dualTutorProfile.getTutorProfileId()).isNotNull();
        assertThat(dualUser.getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    @DisplayName("CP-003.20 - las materias, mercadoPagoLinked y la foto no cambian")
    void updateProfile_preservesSubjectsAndMercadoPagoAndPicture() throws Exception {
        UUID subjectId = virtualSubject.getTutorSubjectId();

        Map<String, Object> payload = validPayload();
        payload.put("biography", "Biografía editada");
        payload.put("address", "Dirección editada 789");

        putProfile(payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects.length()").value(1))
                .andExpect(jsonPath("$.subjects[0].tutorSubjectId").value(subjectId.toString()))
                .andExpect(jsonPath("$.mercadoPagoLinked").value(true))
                .andExpect(jsonPath("$.profilePictureUrl").value("https://cdn.test/tutor.png"));

        TutorProfile reloaded = reloadProfile(tutorUser.getUserId());
        assertThat(reloaded.getSubjects()).hasSize(1);
        assertThat(reloaded.getSubjects().get(0).getTutorSubjectId()).isEqualTo(subjectId);
        assertThat(reloaded.isMercadoPagoLinked()).isTrue();
        assertThat(reloaded.getProfilePictureUrl()).isEqualTo("https://cdn.test/tutor.png");
    }
}
