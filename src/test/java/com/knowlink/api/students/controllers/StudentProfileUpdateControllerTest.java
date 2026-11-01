package com.knowlink.api.students.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.repositories.ICareerRepository;
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
import java.util.List;
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
class StudentProfileUpdateControllerTest {

    private static final String URL = "/api/v1/students/me/profile";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ICareerRepository careerRepository;

    @Autowired
    private IStudentProfileRepository studentProfileRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private Career careerA;
    private Career careerB;
    private User studentUser;
    private StudentProfile studentProfile;
    private User otherUser;

    @BeforeEach
    void seedData() {
        careerA = careerRepository.save(Career.builder().name("Ingeniería en Sistemas US-03").build());
        careerB = careerRepository.save(Career.builder().name("Medicina US-03").build());

        studentUser = userRepository.save(User.builder()
                .firstName("Ana")
                .lastName("Pérez")
                .fullName("Ana Pérez")
                .email("ana.edit.profile@test.com")
                .password("hashed")
                .dni("30111222")
                .phoneNumber("+54 9 3534000000")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        studentProfile = studentProfileRepository.save(StudentProfile.builder()
                .user(studentUser)
                .career(careerA)
                .institutionalId("11111")
                .build());

        otherUser = userRepository.save(User.builder()
                .fullName("Otro Usuario")
                .email("otro.edit.profile@test.com")
                .password("hashed")
                .dni("30999888")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private RequestPostProcessor asUser(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private RequestPostProcessor asTutor() {
        User tutor = User.builder()
                .fullName("Tutor Autenticado")
                .email("tutor.auth.edit.profile@test.com")
                .password("hashed")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        return asUser(tutor);
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("firstName", "Ana");
        payload.put("lastName", "Pérez");
        payload.put("dni", "30111222");
        payload.put("phoneNumber", "+54 9 3534000000");
        payload.put("career", careerA.getName());
        payload.put("institutionalId", "11111");
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
        return putProfile(payload, asUser(studentUser));
    }

    private User reloadUser(UUID userId) {
        entityManager.flush();
        entityManager.clear();
        return userRepository.findById(userId).orElseThrow();
    }

    private StudentProfile reloadProfile(UUID userId) {
        entityManager.flush();
        entityManager.clear();
        return studentProfileRepository.findByUserId(userId).orElseThrow();
    }

    @Test
    @DisplayName("CP-003.1 - 200: actualiza los seis campos y el GET devuelve los valores nuevos")
    void updateProfile_updatesAllFields() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("firstName", "María José");
        payload.put("lastName", "García");
        payload.put("dni", "40123456");
        payload.put("phoneNumber", "+54 9 3534138000");
        payload.put("career", careerB.getName());
        payload.put("institutionalId", "12345");

        putProfile(payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(studentUser.getUserId().toString()))
                .andExpect(jsonPath("$.fullName").value("María José García"))
                .andExpect(jsonPath("$.firstName").value("María José"))
                .andExpect(jsonPath("$.lastName").value("García"))
                .andExpect(jsonPath("$.email").value("ana.edit.profile@test.com"))
                .andExpect(jsonPath("$.phoneNumber").value("+54 9 3534138000"))
                .andExpect(jsonPath("$.dni").value("40123456"))
                .andExpect(jsonPath("$.career").value(careerB.getName()))
                .andExpect(jsonPath("$.institutionalId").value("12345"));

        mockMvc.perform(get(URL).with(asUser(studentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("María José García"))
                .andExpect(jsonPath("$.firstName").value("María José"))
                .andExpect(jsonPath("$.lastName").value("García"))
                .andExpect(jsonPath("$.phoneNumber").value("+54 9 3534138000"))
                .andExpect(jsonPath("$.dni").value("40123456"))
                .andExpect(jsonPath("$.career").value(careerB.getName()))
                .andExpect(jsonPath("$.institutionalId").value("12345"));

        User reloaded = reloadUser(studentUser.getUserId());
        assertThat(reloaded.getFullName()).isEqualTo("María José García");
        assertThat(reloaded.getFirstName()).isEqualTo("María José");
        assertThat(reloaded.getLastName()).isEqualTo("García");
        assertThat(reloaded.getDni()).isEqualTo("40123456");
        assertThat(reloaded.getPhoneNumber()).isEqualTo("+54 9 3534138000");

        StudentProfile reloadedProfile = reloadProfile(studentUser.getUserId());
        assertThat(reloadedProfile.getCareer().getName()).isEqualTo(careerB.getName());
        assertThat(reloadedProfile.getInstitutionalId()).isEqualTo("12345");
    }

    @Test
    @DisplayName("CP-003.2 - 200: el email enviado en el body se descarta y no cambia")
    void updateProfile_ignoresEmail() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("firstName", "Ana María");
        payload.put("email", "otro@x.com");

        putProfile(payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana.edit.profile@test.com"))
                .andExpect(jsonPath("$.firstName").value("Ana María"));

        User reloaded = reloadUser(studentUser.getUserId());
        assertThat(reloaded.getEmail()).isEqualTo("ana.edit.profile@test.com");
    }

    @Test
    @DisplayName("CP-003.3 - 400: campo obligatorio ausente o en blanco no modifica la base")
    void updateProfile_missingRequiredFields() throws Exception {
        for (String field : List.of("firstName", "lastName", "dni", "phoneNumber", "career")) {
            Map<String, Object> missing = validPayload();
            missing.remove(field);
            putProfile(missing)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));

            Map<String, Object> blank = validPayload();
            blank.put(field, "   ");
            putProfile(blank)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));
        }

        User unchanged = reloadUser(studentUser.getUserId());
        assertThat(unchanged.getFullName()).isEqualTo("Ana Pérez");
        assertThat(unchanged.getDni()).isEqualTo("30111222");
        assertThat(unchanged.getPhoneNumber()).isEqualTo("+54 9 3534000000");

        StudentProfile unchangedProfile = reloadProfile(studentUser.getUserId());
        assertThat(unchangedProfile.getCareer().getName()).isEqualTo(careerA.getName());
    }

    @Test
    @DisplayName("CP-003.4 - 400: DNI con formato inválido")
    void updateProfile_invalidDniFormat() throws Exception {
        for (String dni : List.of("123", "123456789", "12a45678")) {
            Map<String, Object> payload = validPayload();
            payload.put("dni", dni);
            putProfile(payload)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));
        }

        assertThat(reloadUser(studentUser.getUserId()).getDni()).isEqualTo("30111222");
    }

    @Test
    @DisplayName("CP-003.5 - 409 DUPLICATE_DNI: el DNI de otro usuario no modifica la base")
    void updateProfile_duplicateDniOfAnotherUser() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("firstName", "Nombre Nuevo");
        payload.put("dni", otherUser.getDni());

        putProfile(payload)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("DUPLICATE_DNI"))
                .andExpect(jsonPath("$.message").value("Este DNI ya está registrado."));

        User unchanged = reloadUser(studentUser.getUserId());
        assertThat(unchanged.getFullName()).isEqualTo("Ana Pérez");
        assertThat(unchanged.getDni()).isEqualTo("30111222");
    }

    @Test
    @DisplayName("CP-003.6 - 200: reenviar el propio DNI no es un duplicado")
    void updateProfile_keepsOwnDni() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("firstName", "Ana María");

        putProfile(payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dni").value("30111222"))
                .andExpect(jsonPath("$.firstName").value("Ana María"));

        assertThat(reloadUser(studentUser.getUserId()).getDni()).isEqualTo("30111222");
    }

    @Test
    @DisplayName("CP-003.7 - 404 CAREER_NOT_FOUND: no se modifica ningún dato")
    void updateProfile_unknownCareer() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("firstName", "Nombre Nuevo");
        payload.put("dni", "40123456");
        payload.put("career", "Carrera Inexistente");

        putProfile(payload)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("CAREER_NOT_FOUND"));

        User unchanged = reloadUser(studentUser.getUserId());
        assertThat(unchanged.getFullName()).isEqualTo("Ana Pérez");
        assertThat(unchanged.getDni()).isEqualTo("30111222");
    }

    @Test
    @DisplayName("CP-003.8 - 200: institutionalId en blanco queda null")
    void updateProfile_blankInstitutionalIdBecomesNull() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("institutionalId", "   ");

        putProfile(payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institutionalId").isEmpty());

        assertThat(reloadProfile(studentUser.getUserId()).getInstitutionalId()).isNull();
    }

    @Test
    @DisplayName("CP-003.9 - 403 con rol TUTOR y 401 sin autenticación")
    void updateProfile_forbiddenAndUnauthorized() throws Exception {
        putProfile(validPayload(), asTutor())
                .andExpect(status().isForbidden());

        mockMvc.perform(put(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validPayload())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CP-003.10 - usuario previo sin first_name/last_name resuelve el nombre desde fullName")
    void getProfile_fallsBackToFullName() throws Exception {
        User legacyUser = userRepository.save(User.builder()
                .fullName("Juan Perez")
                .email("juan.legacy.edit.profile@test.com")
                .password("hashed")
                .dni("30777888")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        studentProfileRepository.save(StudentProfile.builder()
                .user(legacyUser)
                .career(careerA)
                .build());

        mockMvc.perform(get(URL).with(asUser(legacyUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Juan Perez"))
                .andExpect(jsonPath("$.firstName").value("Juan"))
                .andExpect(jsonPath("$.lastName").value("Perez"));
    }
}
