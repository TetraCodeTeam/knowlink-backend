package com.knowlink.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security contract of the admin area: anonymous requests are rejected with 401, STUDENT and TUTOR
 * with 403 and only ADMIN reaches the endpoints. It also checks that an administrator can log in
 * and use the panel without any student or tutor profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String MATERIAL_REPORTS = "/api/v1/admin/material-reports";
    private static final String SESSION_CLAIMS = "/api/v1/admin/session-claims";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ICareerRepository careerRepository;

    @Autowired
    private IStudentProfileRepository studentProfileRepository;

    @Autowired
    private ITutorProfileRepository tutorProfileRepository;

    private User admin;
    private User student;
    private User tutor;

    @BeforeEach
    void setUp() {
        Career career = careerRepository.save(Career.builder()
                .name("Career " + UUID.randomUUID())
                .build());
        admin = saveUser(Role.ADMIN);
        student = saveUser(Role.STUDENT);
        studentProfileRepository.save(StudentProfile.builder()
                .user(student)
                .career(career)
                .build());
        tutor = saveUser(Role.TUTOR);
        tutorProfileRepository.save(TutorProfile.builder()
                .user(tutor)
                .career(career)
                .verified(true)
                .build());
    }

    @Test
    @DisplayName("Materiales: sin token devuelve 401")
    void materialReports_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(MATERIAL_REPORTS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Materiales: STUDENT recibe 403 ACCESS_DENIED")
    void materialReports_withStudent_returns403() throws Exception {
        mockMvc.perform(get(MATERIAL_REPORTS)
                        .header("Authorization", "Bearer " + login(student)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Materiales: TUTOR recibe 403 ACCESS_DENIED")
    void materialReports_withTutor_returns403() throws Exception {
        mockMvc.perform(get(MATERIAL_REPORTS)
                        .header("Authorization", "Bearer " + login(tutor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Materiales: ADMIN recibe 200 sin necesitar perfil de alumno ni de tutor")
    void materialReports_withAdmin_returns200() throws Exception {
        mockMvc.perform(get(MATERIAL_REPORTS)
                        .header("Authorization", "Bearer " + login(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content").isArray())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.statusCounts.pending").value(0));
    }

    @Test
    @DisplayName("Reclamos: sin token devuelve 401")
    void sessionClaims_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(SESSION_CLAIMS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Reclamos: TUTOR recibe 403 ACCESS_DENIED")
    void sessionClaims_withTutor_returns403() throws Exception {
        mockMvc.perform(get(SESSION_CLAIMS)
                        .header("Authorization", "Bearer " + login(tutor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Reclamos: STUDENT recibe 403 ACCESS_DENIED")
    void sessionClaims_withStudent_returns403() throws Exception {
        mockMvc.perform(get(SESSION_CLAIMS)
                        .header("Authorization", "Bearer " + login(student)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Reclamos: ADMIN recibe 200 sin perfil asociado")
    void sessionClaims_withAdmin_returns200() throws Exception {
        mockMvc.perform(get(SESSION_CLAIMS)
                        .header("Authorization", "Bearer " + login(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content").isArray())
                .andExpect(jsonPath("$.statusCounts.open").value(0));
    }

    @Test
    @DisplayName("Login de administrador devuelve role ADMIN")
    void login_admin_returnsAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    private User saveUser(Role role) {
        return userRepository.save(User.builder()
                .fullName(role.name() + " Security")
                .email(role.name().toLowerCase() + "." + UUID.randomUUID() + "@security.test")
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private String login(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String loginBody(User user) {
        return "{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}";
    }
}