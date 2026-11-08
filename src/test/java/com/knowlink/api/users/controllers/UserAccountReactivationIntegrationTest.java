package com.knowlink.api.users.controllers;

import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.testsupport.CatalogFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAccountReactivationIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REACTIVATE_PATH = "/api/v1/auth/reactivate";
    private static final String DEACTIVATED_MESSAGE =
            "Tu cuenta está desactivada. Podés reactivarla para volver a usar KnowLink.";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IUserRepository userRepository;
    @Autowired
    private IStudentProfileRepository studentProfileRepository;
    @Autowired
    private ICareerRepository careerRepository;

    @Autowired
    private IInstitutionRepository institutionRepository;

    private Institution institution;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User student;
    private UUID studentProfileId;

    @BeforeEach
    void setUp() {
        institution = CatalogFixtures.saveInstitution(institutionRepository, "Test " + System.nanoTime());
        Career career = careerRepository.save(Career.builder().institution(institution)
                .name("Career " + UUID.randomUUID())
                .build());
        student = saveUser("Student Reactivation", Role.STUDENT);
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .user(student)
                .career(career)
                .createdAt(LocalDateTime.now())
                .build());
        studentProfileId = profile.getStudentProfileId();
    }

    @Test
    @DisplayName("Reactivating a DEACTIVATED account returns 200 and the new JWT works")
    void reactivateDeactivated_returns200_andNewTokenWorks() throws Exception {
        setStatus(student, AccountStatus.DEACTIVATED);

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(student.getEmail(), PASSWORD, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("ACCOUNT_DEACTIVATED"))
                .andExpect(jsonPath("$.message").value(DEACTIVATED_MESSAGE));

        String response = mockMvc.perform(post(REACTIVATE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(student.getEmail(), PASSWORD, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("token").asText();

        User updated = userRepository.findById(student.getUserId()).orElseThrow();
        assertThat(updated.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(updated.getDeactivatedAt()).isNull();
        assertThat(studentProfileRepository.findById(studentProfileId)).isPresent();

        mockMvc.perform(get("/api/v1/users/{id}", student.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(student.getUserId().toString()));
    }

    @Test
    @DisplayName("Reactivating with a wrong password returns 401 and keeps the account DEACTIVATED")
    void reactivateWrongPassword_returns401_andKeepsDeactivated() throws Exception {
        setStatus(student, AccountStatus.DEACTIVATED);

        mockMvc.perform(post(REACTIVATE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(student.getEmail(), "WrongPassword123!", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        assertThat(userRepository.findById(student.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.DEACTIVATED);
    }

    @Test
    @DisplayName("Reactivating an unknown email returns 401")
    void reactivateUnknownEmail_returns401() throws Exception {
        mockMvc.perform(post(REACTIVATE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody("missing." + UUID.randomUUID() + "@test.com", PASSWORD, null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    @DisplayName("Reactivating an INACTIVE account (email not confirmed) returns 401")
    void reactivateInactive_returns401() throws Exception {
        setStatus(student, AccountStatus.INACTIVE);

        mockMvc.perform(post(REACTIVATE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(student.getEmail(), PASSWORD, null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Account not active"));

        assertThat(userRepository.findById(student.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.INACTIVE);
    }

    @Test
    @DisplayName("Reactivating a DELETED account returns 401")
    void reactivateDeleted_returns401() throws Exception {
        setStatus(student, AccountStatus.DELETED);

        mockMvc.perform(post(REACTIVATE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(student.getEmail(), PASSWORD, null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Account locked"));

        assertThat(userRepository.findById(student.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.DELETED);
    }

    @Test
    @DisplayName("Reactivating an already ACTIVE account is idempotent and returns 200")
    void reactivateActive_returns200_idempotent() throws Exception {
        mockMvc.perform(post(REACTIVATE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(student.getEmail(), PASSWORD, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        assertThat(userRepository.findById(student.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Reactivating with a targetRole without a profile returns 400 and rolls back to DEACTIVATED")
    void reactivateTargetRoleWithoutProfile_returns400_andRollsBack() throws Exception {
        setStatus(student, AccountStatus.DEACTIVATED);

        mockMvc.perform(post(REACTIVATE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(student.getEmail(), PASSWORD, Role.TUTOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));

        assertThat(userRepository.findById(student.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.DEACTIVATED);
    }

    private User saveUser(String fullName, Role role) {
        return userRepository.save(User.builder()
                .fullName(fullName)
                .email(fullName.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID() + "@test.com")
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private void setStatus(User user, AccountStatus status) {
        user.setAccountStatus(status);
        user.setDeactivatedAt(status == AccountStatus.DEACTIVATED ? LocalDateTime.now() : null);
        userRepository.save(user);
    }

    private String authBody(String email, String password, Role targetRole) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        if (targetRole != null) {
            body.put("targetRole", targetRole);
        }
        return objectMapper.writeValueAsString(body);
    }
}