package com.knowlink.api.claims.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.claims.data.models.ClaimAttachment;
import com.knowlink.api.claims.repositories.ClaimAttachmentRepository;
import com.knowlink.api.claims.repositories.SessionClaimRepository;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
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

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2E real: crea un reclamo con adjunto contra el bucket "claimEvidence" de Supabase
 * usando el storage real (sin mocks). Se salta si no hay credenciales configuradas
 * (variable de entorno o .env del proyecto).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Slf4j
@EnabledIf("supabaseConfigured")
class ClaimAttachmentRealUploadIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String BUCKET = "claimEvidence";

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
    private IStudentProfileRepository studentProfileRepository;

    @Autowired
    private ICareerRepository careerRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private ITutorProfileRepository tutorProfileRepository;

    @Autowired
    private ITutorSubjectRepository tutorSubjectRepository;

    @Autowired
    private ITimeSlotRepository timeSlotRepository;

    @Autowired
    private IBookingRepository bookingRepository;

    @Autowired
    private IFundsTransferRepository fundsTransferRepository;

    @Autowired
    private SessionClaimRepository sessionClaimRepository;

    @Autowired
    private ClaimAttachmentRepository claimAttachmentRepository;

    @Autowired
    private ISupabaseStorageService storageService;

    private User student;
    private User tutor;
    private TutorProfile tutorProfile;
    private TutorSubject tutorSubject;

    @BeforeEach
    void setUp() {
        Career career = careerRepository.save(Career.builder()
                .name("Career " + UUID.randomUUID()).build());
        Subject subject = subjectRepository.save(Subject.builder()
                .name("Subject " + UUID.randomUUID()).isBasic(true).career(career).build());
        tutor = saveUser("Tutor Test", Role.TUTOR);
        tutorProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(tutor).career(career).verified(true).build());
        tutorSubject = tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .pricePerHour(new BigDecimal("2000.00"))
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.BOTH)
                .build());
        student = saveUser("Student Test", Role.STUDENT);
        studentProfileRepository.save(StudentProfile.builder().user(student).career(career).build());
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

    private String login(User user) throws Exception {
        String body = "{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private Booking withinDeadlineBooking() {
        TimeSlot slot = timeSlotRepository.save(TimeSlot.builder()
                .date(LocalDate.now())
                .startTime(LocalTime.NOON)
                .endTime(LocalTime.of(15, 0))
                .status(SlotStatus.OCCUPIED)
                .tutorProfileId(tutorProfile.getTutorProfileId())
                .build());
        return bookingRepository.save(Booking.builder()
                .amount(new BigDecimal("2100.00"))
                .sessionDate(LocalDate.now())
                .startTime(LocalTime.NOON)
                .endTime(LocalTime.of(15, 0))
                .topic("Real upload E2E topic")
                .modality(Modality.VIRTUAL)
                .bookingStatus(BookingStatus.COMPLETED)
                .timeSlot(slot)
                .tutorSubject(tutorSubject)
                .student(student)
                .tutor(tutor)
                .build());
    }

    private void heldTransfer(Booking booking) {
        fundsTransferRepository.save(FundsTransfer.builder()
                .booking(booking)
                .originalAmount(booking.getAmount())
                .transferredAmount(BigDecimal.ZERO)
                .recipient(FundsRecipient.TUTOR)
                .concept("Fondos retenidos para reserva")
                .fundsStatus(FundsStatus.HELD)
                .build());
    }

    private static byte[] realPdfBytes() {
        String pdf = "%PDF-1.4\n"
                + "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
                + "2 0 obj << /Type /Pages /Kids [] /Count 0 >> endobj\n"
                + "trailer << /Root 1 0 R >>\n"
                + "%%EOF\n";
        return pdf.getBytes(StandardCharsets.US_ASCII);
    }

    private MockHttpServletRequestBuilder postClaim(String token, UUID bookingId, String requestJson,
            MockMultipartFile file) {
        return multipart("/api/v1/bookings/{bookingId}/claims", bookingId)
                .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                        requestJson.getBytes(StandardCharsets.UTF_8)))
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    @Test
    @DisplayName("E2E - el reclamo sube el adjunto al bucket claimEvidence real y la signed URL lo sirve")
    void createClaimWithAttachment_uploadsToRealBucketAndSignedUrlServes() throws Exception {
        String effectiveUrl = environment.getProperty("supabase.url");
        Assumptions.assumeTrue(
                effectiveUrl != null && effectiveUrl.startsWith("https://")
                        && !effectiveUrl.contains("test.supabase.co"),
                "supabase.url real no disponible: se esta usando el valor de prueba");

        Booking booking = withinDeadlineBooking();
        heldTransfer(booking);
        String token = login(student);
        byte[] pdfBytes = realPdfBytes();
        MockMultipartFile file = new MockMultipartFile(
                "files", "evidencia-real.pdf", "application/pdf", pdfBytes);

        MvcResult created = mockMvc.perform(postClaim(token, booking.getBookingId(),
                        "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\",\"comment\":\"e2e real upload\"}", file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].fileName").value("evidencia-real.pdf"))
                .andReturn();
        JsonNode claimJson = objectMapper.readTree(created.getResponse().getContentAsString());
        String claimId = claimJson.get("id").asText();

        List<ClaimAttachment> attachments =
                claimAttachmentRepository.findByClaim_ClaimId(UUID.fromString(claimId));
        assertThat(attachments).hasSize(1);
        String storagePath = attachments.get(0).getStoragePath();

        boolean deletedFromBucket = false;
        try {
            assertThat(storagePath)
                    .startsWith(booking.getBookingId().toString() + "/")
                    .contains("evidencia-real.pdf");

            MvcResult signedResult = mockMvc.perform(get(
                            "/api/v1/bookings/{bookingId}/claims/{claimId}/attachments",
                            booking.getBookingId(), claimId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].signedUrl").isNotEmpty())
                    .andReturn();
            JsonNode signed = objectMapper.readTree(signedResult.getResponse().getContentAsString()).get(0);
            String signedUrl = signed.get("signedUrl").asText();
            assertThat(signedUrl)
                    .contains("/storage/v1/object/sign/" + BUCKET + "/")
                    .doesNotContain("test.supabase.co");

            HttpResponse<byte[]> served = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(signedUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(served.statusCode()).isEqualTo(200);
            assertThat(served.body()).isEqualTo(pdfBytes);

            storageService.delete(BUCKET, storagePath);
            deletedFromBucket = true;

            HttpClient client = HttpClient.newHttpClient();
            String directUrl = effectiveUrl + "/storage/v1/object/" + BUCKET + "/" + storagePath;
            HttpRequest directRequest = HttpRequest.newBuilder(URI.create(directUrl))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveSupabaseValue("SUPABASE_SERVICE_ROLE_KEY"))
                    .GET()
                    .build();
            int directStatus = 200;
            long deadline = System.currentTimeMillis() + 30000;
            int probes = 0;
            while (directStatus == 200 && System.currentTimeMillis() < deadline) {
                probes++;
                directStatus = client.send(directRequest, HttpResponse.BodyHandlers.ofByteArray()).statusCode();
                log.info("Probe #{} after delete: directStorage={}", probes, directStatus);
                if (directStatus != 200) {
                    break;
                }
                Thread.sleep(1000);
            }
            assertThat(directStatus)
                    .as("el objeto ya no existe en el bucket luego del delete (sondas=%d)", probes)
                    .isGreaterThanOrEqualTo(400);

            assertThatThrownBy(() -> storageService.generateSignedUrl(BUCKET, storagePath, 3600))
                    .as("re-firmar un objeto borrado fallia con error del backend de storage")
                    .isInstanceOf(RestClientResponseException.class);

            FundsTransfer transfer = fundsTransferRepository
                    .findByBooking_BookingId(booking.getBookingId()).orElseThrow();
            assertThat(transfer.getFundsStatus()).isEqualTo(FundsStatus.SUSPENDED_BY_CLAIM);
            assertThat(Objects.toString(transfer.getBlockingClaimId(), null)).isEqualTo(claimId);

            long claimRows = sessionClaimRepository.findAll().stream()
                    .filter(c -> c.getActiveKey() != null
                            && c.getActiveKey().startsWith(booking.getBookingId().toString()))
                    .count();
            assertThat(claimRows).isEqualTo(1);
        } finally {
            if (!deletedFromBucket) {
                try {
                    storageService.delete(BUCKET, storagePath);
                } catch (Exception cleanupEx) {
                    log.warn("Best-effort cleanup failed for {}: {}", storagePath, cleanupEx.getMessage());
                }
            }
        }
    }
}