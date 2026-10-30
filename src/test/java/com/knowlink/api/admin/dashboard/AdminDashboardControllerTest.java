package com.knowlink.api.admin.dashboard;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDashboardControllerTest {

    private static final String PASSWORD = "Password123!";

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
    @DisplayName("Badges: ADMIN recibe 200 con ambos contadores")
    void badges_withAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/navigation/badges")
                        .header("Authorization", "Bearer " + login(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingMaterialReports").isNumber())
                .andExpect(jsonPath("$.pendingSessionClaims").isNumber());
    }

    @Test
    @DisplayName("Resumen: ADMIN recibe 200 y el rango solicitado")
    void summary_withAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .param("range", "MONTH")
                        .header("Authorization", "Bearer " + login(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.performance.range").value("MONTH"))
                .andExpect(jsonPath("$.performance.unresolvedLevel").exists())
                .andExpect(jsonPath("$.performance.volumeSplit.claimsPct").isNumber())
                .andExpect(jsonPath("$.performance.volumeSplit.reportsPct").isNumber())
                .andExpect(jsonPath("$.sessionClaims.pendingCount").isNumber())
                .andExpect(jsonPath("$.materialReports.pendingCount").isNumber())
                .andExpect(jsonPath("$.materialReports.pendingReports").isNumber());
    }

    @Test
    @DisplayName("Resumen: rango inválido devuelve 400 VALIDATION_ERROR")
    void summary_withInvalidRange_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .param("range", "YEAR")
                        .header("Authorization", "Bearer " + login(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Badges: STUDENT recibe 403 ACCESS_DENIED")
    void badges_withStudent_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/navigation/badges")
                        .header("Authorization", "Bearer " + login(student)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Resumen: TUTOR recibe 403 ACCESS_DENIED")
    void summary_withTutor_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .header("Authorization", "Bearer " + login(tutor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Badges: sin token devuelve 401")
    void badges_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/navigation/badges"))
                .andExpect(status().isUnauthorized());
    }

    private User saveUser(Role role) {
        return userRepository.save(User.builder()
                .fullName(role.name() + " Dashboard")
                .email(role.name().toLowerCase() + "." + UUID.randomUUID() + "@test.com")
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private String login(User user) throws Exception {
        String body = "{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
