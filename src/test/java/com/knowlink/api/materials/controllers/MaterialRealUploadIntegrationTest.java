package com.knowlink.api.materials.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2E real: sube un material académico al bucket "materiales" de Supabase
 * usando el storage real (sin mocks). Se salta si no hay credenciales configuradas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Slf4j
@EnabledIf("supabaseConfigured")
class MaterialRealUploadIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String BUCKET = "materiales";

    static boolean supabaseConfigured() {
        String url = resolveSupabaseValue("SUPABASE_URL");
        String key = resolveSupabaseValue("SUPABASE_SERVICE_ROLE_KEY");
        return url != null && url.startsWith("http") && !url.contains("test.supabase.co")
                && key != null && !key.isBlank();
    }

    private static String resolveSupabaseValue(String name) {
        String fromEnv = System.getenv(name);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        Path envFile = Path.of(".env");
        if (!Files.isRegularFile(envFile)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq > 0 && trimmed.substring(0, eq).trim().equals(name)) {
                    String value = trimmed.substring(eq + 1).trim();
                    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            }
        } catch (Exception ex) {
            return null;
        }
        return null;
    }

    @DynamicPropertySource
    static void realSupabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("supabase.url", () -> resolveSupabaseValue("SUPABASE_URL"));
        registry.add("supabase.service-role-key", () -> resolveSupabaseValue("SUPABASE_SERVICE_ROLE_KEY"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ICareerRepository careerRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private ITutorProfileRepository tutorProfileRepository;

    @Autowired
    private IAcademicMaterialRepository materialRepository;

    @Autowired
    private ISupabaseStorageService storageService;

    private static byte[] realPdfBytes() {
        String pdf = "%PDF-1.4\n"
                + "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
                + "2 0 obj << /Type /Pages /Kids [] /Count 0 >> endobj\n"
                + "trailer << /Root 1 0 R >>\n"
                + "%%EOF\n";
        return pdf.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    @DisplayName("E2E - el material se sube al bucket materiales real y la signed URL lo sirve")
    void uploadMaterial_uploadsToRealBucketAndSignedUrlServes() throws Exception {
        String effectiveUrl = environment.getProperty("supabase.url");
        Assumptions.assumeTrue(
                effectiveUrl != null && effectiveUrl.startsWith("https://")
                        && !effectiveUrl.contains("test.supabase.co"),
                "supabase.url real no disponible: se esta usando el valor de prueba");

        Career career = careerRepository.save(Career.builder()
                .name("Career " + UUID.randomUUID()).build());
        Subject subject = subjectRepository.save(Subject.builder()
                .name("Subject " + UUID.randomUUID()).isBasic(true).career(career).build());

        User tutor = userRepository.save(User.builder()
                .fullName("Tutor Material Test")
                .email("tutor.material." + UUID.randomUUID() + "@test.com")
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        TutorProfile tutorProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(tutor).career(career).verified(true).build());

        String token = login(tutor);
        byte[] pdfBytes = realPdfBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "material-real.pdf", "application/pdf", pdfBytes);

        String storagePath = null;
        try {
            MvcResult created = mockMvc.perform(multipart("/api/v1/materials")
                            .file(file)
                            .param("name", "Material E2E Real")
                            .param("subjectId", subject.getSubjectId().toString())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Material E2E Real"))
                    .andExpect(jsonPath("$.originalFileName").value("material-real.pdf"))
                    .andExpect(jsonPath("$.format").value("PDF"))
                    .andReturn();

            JsonNode json = objectMapper.readTree(created.getResponse().getContentAsString());
            UUID materialId = UUID.fromString(json.get("id").asText());

            AcademicMaterial material = materialRepository.findById(materialId).orElseThrow();
            storagePath = material.getStoragePath();

            assertThat(storagePath)
                    .startsWith(subject.getSubjectId().toString() + "/")
                    .contains("material-real.pdf");

            MvcResult download = mockMvc.perform(get("/api/v1/materials/{id}/download", materialId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();

            String rawUrl = download.getResponse().getContentAsString().trim();
            assertThat(rawUrl)
                    .contains("/storage/v1/object/sign/" + BUCKET + "/")
                    .doesNotContain("test.supabase.co");

            HttpResponse<byte[]> served = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(rawUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(served.statusCode()).isEqualTo(200);
            assertThat(served.body()).isEqualTo(pdfBytes);

            log.info("Material E2E verified: uploaded and served from bucket {}", BUCKET);
        } finally {
            if (storagePath != null) {
                try {
                    storageService.delete(BUCKET, storagePath);
                } catch (Exception cleanupEx) {
                    log.warn("Best-effort cleanup failed for {}: {}", storagePath, cleanupEx.getMessage());
                }
            }
        }
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
