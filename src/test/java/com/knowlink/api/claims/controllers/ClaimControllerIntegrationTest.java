package com.knowlink.api.claims.controllers;
import com.knowlink.api.catalog.data.models.Institution;
import com.knowlink.api.catalog.repositories.IInstitutionRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.bookings.data.enums.BookingHistoryCategory;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.claims.controllers.requests.CreateClaimRequest;
import com.knowlink.api.claims.data.enums.ClaimReason;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.repositories.SessionClaimRepository;
import com.knowlink.api.claims.services.interfaces.ISessionClaimService;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;
import com.knowlink.api.payments.services.FundsLedgerService;
import com.knowlink.api.resources.service.interfaces.ISupabaseStorageService;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClaimControllerIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String ACTIVE_CLAIM_MESSAGE = "Ya tenés una disputa activa para esta sesión";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private ISessionClaimService sessionClaimService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ISupabaseStorageService storageService;

    @MockitoSpyBean
    private FundsLedgerService fundsLedgerService;

    private User student;
    private User tutor;
    private User outsider;
    private TutorProfile tutorProfile;
    private TutorSubject tutorSubject;

    @Autowired
    private IInstitutionRepository institutionRepository;

    @BeforeEach
    void setUp() {
        Institution institution = institutionRepository.save(Institution.builder()
                .name("Test Institution " + UUID.randomUUID())
                .build());
        Career career = careerRepository.save(Career.builder()
                .name("Career " + UUID.randomUUID())
                .institution(institution)
                .build());
        Subject subject = subjectRepository.save(Subject.builder()
                .name("Subject " + UUID.randomUUID())
                .isBasic(true)
                .institution(institution)
                .careers(java.util.Set.of(career))
                .build());
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
        outsider = saveUser("Outsider Test", Role.STUDENT);
        studentProfileRepository.save(StudentProfile.builder().user(outsider).career(career).build());

        when(storageService.upload(any(MultipartFile.class), anyString(), anyString()))
                .thenAnswer(invocation -> "claimEvidence/" + UUID.randomUUID());
        when(storageService.generateSignedUrl(anyString(), anyString(), anyInt()))
                .thenReturn("https://signed.test/asset");
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

    private Booking createBooking(LocalDate sessionDate, LocalTime startTime, LocalTime endTime,
            BookingStatus status) {
        TimeSlot slot = timeSlotRepository.save(TimeSlot.builder()
                .date(sessionDate)
                .startTime(startTime)
                .endTime(endTime)
                .status(SlotStatus.OCCUPIED)
                .tutorProfileId(tutorProfile.getTutorProfileId())
                .build());
        return bookingRepository.save(Booking.builder()
                .amount(new BigDecimal("2100.00"))
                .sessionDate(sessionDate)
                .startTime(startTime)
                .endTime(endTime)
                .topic("Integration topic")
                .modality(Modality.VIRTUAL)
                .bookingStatus(status)
                .timeSlot(slot)
                .tutorSubject(tutorSubject)
                .student(student)
                .tutor(tutor)
                .build());
    }

    private Booking withinDeadlineBooking() {
        return createBooking(LocalDate.now(), LocalTime.NOON, LocalTime.of(15, 0), BookingStatus.COMPLETED);
    }

    private Booking expiredBooking() {
        return createBooking(LocalDate.now().minusDays(5), LocalTime.NOON, LocalTime.of(15, 0),
                BookingStatus.COMPLETED);
    }

    private FundsTransfer heldTransfer(Booking booking) {
        return fundsTransferRepository.save(FundsTransfer.builder()
                .booking(booking)
                .originalAmount(booking.getAmount())
                .transferredAmount(BigDecimal.ZERO)
                .recipient(FundsRecipient.TUTOR)
                .concept("Fondos retenidos para reserva")
                .fundsStatus(FundsStatus.HELD)
                .build());
    }

    private static MockMultipartFile pdfFile(String name) {
        return new MockMultipartFile("files", name, "application/pdf", "pdf-bytes".getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile pngFile(String name) {
        return new MockMultipartFile("files", name, "image/png", "png-bytes".getBytes(StandardCharsets.UTF_8));
    }

    private MockHttpServletRequestBuilder postClaim(String token, UUID bookingId, String requestJson,
            MockMultipartFile... files) {
        var builder = multipart("/api/v1/bookings/{bookingId}/claims", bookingId)
                .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                        requestJson.getBytes(StandardCharsets.UTF_8)));
        for (MockMultipartFile file : files) {
            builder = builder.file(file);
        }
        if (token == null || token.isEmpty()) {
            return builder;
        }
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private long claimCount(Booking booking) {
        return sessionClaimRepository.findAll().stream()
                .filter(claim -> claim.getActiveKey() != null
                        && claim.getActiveKey().startsWith(booking.getBookingId().toString()))
                .count();
    }

    private JsonNode findByBookingId(JsonNode content, UUID bookingId) {
        for (JsonNode item : content) {
            if (bookingId.toString().equals(item.get("bookingId").asText())) {
                return item;
            }
        }
        return null;
    }

    @Test
    @DisplayName("CP-01 - reclamo con adjuntos: 201, fondos suspendidos y adjuntos persistidos")
    void create_withAttachments_returns201AndSuspendsFunds() throws Exception {
        Booking booking = withinDeadlineBooking();
        heldTransfer(booking);
        String token = login(student);
        String json = "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\",\"comment\":\"no pude conectarme\"}";

        mockMvc.perform(postClaim(token, booking.getBookingId(), json, pdfFile("evidence.pdf"), pngFile("photo.png")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.claimantRole").value("STUDENT"))
                .andExpect(jsonPath("$.reason").value("STUDENT_COULD_NOT_ATTEND"))
                .andExpect(jsonPath("$.bookingId").value(booking.getBookingId().toString()))
                .andExpect(jsonPath("$.comment").value("no pude conectarme"))
                .andExpect(jsonPath("$.attachments.length()").value(2))
                .andExpect(jsonPath("$.attachments[0].fileName").value("evidence.pdf"))
                .andExpect(jsonPath("$.attachments[1].fileName").value("photo.png"));

        assertThat(sessionClaimRepository.existsByBooking_BookingIdAndStatus(
                booking.getBookingId(), ClaimStatus.OPEN)).isTrue();
        FundsTransfer updated = fundsTransferRepository.findByBooking_BookingId(booking.getBookingId()).orElseThrow();
        assertThat(updated.getFundsStatus()).isEqualTo(FundsStatus.SUSPENDED_BY_CLAIM);
        assertThat(updated.getBlockingClaimId()).isNotNull();
    }

    @Test
    @DisplayName("CP-02 - sesion gratuita: 201 sin movimiento en el libro de fondos")
    void create_freeSession_returns201WithoutLedgerMovement() throws Exception {
        Booking booking = withinDeadlineBooking();
        String token = login(student);

        mockMvc.perform(postClaim(token, booking.getBookingId(),
                        "{\"reason\":\"I_COULD_NOT_ATTEND\"}", pdfFile("a.pdf")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));

        assertThat(fundsTransferRepository.findByBooking_BookingId(booking.getBookingId())).isEmpty();
        assertThat(claimCount(booking)).isEqualTo(1);
    }

    @Test
    @DisplayName("CP-03 - segundo reclamo: 409 con mensaje exacto y una sola fila en BD")
    void create_duplicateSecondRequest_returns409WithExactMessage() throws Exception {
        Booking booking = withinDeadlineBooking();
        String token = login(student);
        String json = "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\"}";

        mockMvc.perform(postClaim(token, booking.getBookingId(), json, pdfFile("a.pdf")))
                .andExpect(status().isCreated());

        mockMvc.perform(postClaim(token, booking.getBookingId(), json, pdfFile("b.pdf")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ACTIVE_CLAIM_MESSAGE))
                .andExpect(jsonPath("$.detail").value("ACTIVE_CLAIM_EXISTS"));

        assertThat(claimCount(booking)).isEqualTo(1);
    }

    @Test
    @DisplayName("CP-04 - usuario ajeno a la sesion: 403 SESSION_NOT_PARTICIPANT")
    void create_notParticipant_returns403() throws Exception {
        Booking booking = withinDeadlineBooking();
        String token = login(outsider);

        mockMvc.perform(postClaim(token, booking.getBookingId(), "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("SESSION_NOT_PARTICIPANT"));
    }

    @Test
    @DisplayName("CP-05 - sesion inexistente: 404 SESSION_NOT_FOUND")
    void create_unknownBooking_returns404() throws Exception {
        String token = login(student);

        mockMvc.perform(postClaim(token, UUID.randomUUID(), "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("CP-06 - sesion no finalizada: 422 SESSION_NOT_FINALIZED")
    void create_notFinalized_returns422() throws Exception {
        Booking booking = createBooking(LocalDate.now(), LocalTime.NOON, LocalTime.of(15, 0),
                BookingStatus.IN_PROGRESS);
        String token = login(student);

        mockMvc.perform(postClaim(token, booking.getBookingId(), "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("SESSION_NOT_FINALIZED"));
    }

    @Test
    @DisplayName("CP-07 - plazo de reclamo vencido: 422 CLAIM_DEADLINE_EXCEEDED")
    void create_deadlineExpired_returns422() throws Exception {
        Booking booking = expiredBooking();
        String token = login(student);

        mockMvc.perform(postClaim(token, booking.getBookingId(), "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("CLAIM_DEADLINE_EXCEEDED"));

        assertThat(claimCount(booking)).isZero();
    }

    @Test
    @DisplayName("CP-08 - sin autenticar: 401")
    void create_unauthenticated_returns401() throws Exception {
        Booking booking = withinDeadlineBooking();

        mockMvc.perform(postClaim("", booking.getBookingId(), "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CP-09 - motivo ausente: 400")
    void create_missingReason_returns400() throws Exception {
        Booking booking = withinDeadlineBooking();
        String token = login(student);

        mockMvc.perform(postClaim(token, booking.getBookingId(), "{\"comment\":\"hola\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-10 - comentario de mas de 500 caracteres: 400")
    void create_commentTooLong_returns400() throws Exception {
        Booking booking = withinDeadlineBooking();
        String token = login(student);
        String longComment = "x".repeat(501);

        mockMvc.perform(postClaim(token, booking.getBookingId(),
                        "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\",\"comment\":\"" + longComment + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-11 - JSON invalido en el multipart: 400 VALIDATION_ERROR")
    void create_malformedJson_returns400() throws Exception {
        Booking booking = withinDeadlineBooking();
        String token = login(student);

        mockMvc.perform(postClaim(token, booking.getBookingId(), "{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cuerpo de la solicitud inválido."))
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("CP-12 - mas de 3 adjuntos: 400 con mensaje de validacion")
    void create_tooManyAttachments_returns400() throws Exception {
        Booking booking = withinDeadlineBooking();
        String token = login(student);

        mockMvc.perform(postClaim(token, booking.getBookingId(), "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\"}",
                        pdfFile("a.pdf"), pdfFile("b.pdf"), pdfFile("c.pdf"), pdfFile("d.pdf")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No podés adjuntar más de 3 archivos."));
    }

    @Test
    @DisplayName("CP-13 - formato no permitido: 400 FORMAT_NOT_ALLOWED")
    void create_disallowedFormat_returns400() throws Exception {
        Booking booking = withinDeadlineBooking();
        String token = login(student);
        MockMultipartFile zip = new MockMultipartFile("files", "evidence.zip", "application/zip",
                "zip-bytes".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(postClaim(token, booking.getBookingId(),
                        "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\"}", zip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("FORMAT_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("CP-14 - elegibilidad sin reclamo: 200 con canClaim true y fecha limite")
    void eligibility_canClaimTrue_returns200() throws Exception {
        Booking booking = withinDeadlineBooking();
        String token = login(student);

        mockMvc.perform(get("/api/v1/bookings/{bookingId}/claims/eligibility", booking.getBookingId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canClaim").value(true))
                .andExpect(jsonPath("$.claimableUntil").isNotEmpty());
    }

    @Test
    @DisplayName("CP-15 - elegibilidad con reclamo activo: bloqueo ACTIVE_CLAIM_EXISTS")
    void eligibility_afterClaim_blocked() throws Exception {
        Booking booking = withinDeadlineBooking();
        String token = login(student);
        mockMvc.perform(postClaim(token, booking.getBookingId(), "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/bookings/{bookingId}/claims/eligibility", booking.getBookingId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canClaim").value(false))
                .andExpect(jsonPath("$.blockReason").value("ACTIVE_CLAIM_EXISTS"));
    }

    @Test
    @DisplayName("CP-16 - elegibilidad vencida: bloqueo CLAIM_DEADLINE_EXCEEDED")
    void eligibility_expired_blocked() throws Exception {
        Booking booking = expiredBooking();
        String token = login(student);

        mockMvc.perform(get("/api/v1/bookings/{bookingId}/claims/eligibility", booking.getBookingId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canClaim").value(false))
                .andExpect(jsonPath("$.blockReason").value("CLAIM_DEADLINE_EXCEEDED"));
    }

    @Test
    @DisplayName("CP-17 - elegibilidad sin finalizar: bloqueo SESSION_NOT_FINALIZED sin fecha")
    void eligibility_notFinalized_blocked() throws Exception {
        Booking booking = createBooking(LocalDate.now(), LocalTime.NOON, LocalTime.of(15, 0),
                BookingStatus.BOOKED);
        String token = login(student);

        mockMvc.perform(get("/api/v1/bookings/{bookingId}/claims/eligibility", booking.getBookingId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canClaim").value(false))
                .andExpect(jsonPath("$.blockReason").value("SESSION_NOT_FINALIZED"));
    }

    @Test
    @DisplayName("CP-18 - adjuntos: 200 con URL firmada para el participante")
    void attachments_participant_returnsSignedUrls() throws Exception {
        Booking booking = withinDeadlineBooking();
        String token = login(student);
        MvcResult created = mockMvc.perform(postClaim(token, booking.getBookingId(),
                        "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\"}", pdfFile("evidence.pdf")))
                .andExpect(status().isCreated())
                .andReturn();
        String claimId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/bookings/{bookingId}/claims/{claimId}/attachments",
                        booking.getBookingId(), claimId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fileName").value("evidence.pdf"))
                .andExpect(jsonPath("$[0].signedUrl").value("https://signed.test/asset"));
    }

    @Test
    @DisplayName("CP-19 - adjuntos ajenos: 403 SESSION_NOT_PARTICIPANT")
    void attachments_outsider_returns403() throws Exception {
        Booking booking = withinDeadlineBooking();
        String studentToken = login(student);
        MvcResult created = mockMvc.perform(postClaim(studentToken, booking.getBookingId(),
                        "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String claimId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        String outsiderToken = login(outsider);

        mockMvc.perform(get("/api/v1/bookings/{bookingId}/claims/{claimId}/attachments",
                        booking.getBookingId(), claimId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("SESSION_NOT_PARTICIPANT"));
    }

    @Test
    @DisplayName("CP-20 - adjuntos de reclamo inexistente: 404 CLAIM_NOT_FOUND")
    void attachments_unknownClaim_returns404() throws Exception {
        Booking booking = withinDeadlineBooking();
        String token = login(student);

        mockMvc.perform(get("/api/v1/bookings/{bookingId}/claims/{claimId}/attachments",
                        booking.getBookingId(), UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("CLAIM_NOT_FOUND"));
    }

    @Test
    @DisplayName("CP-21 - listado /mine expone canClaim y claimableUntil por reserva")
    void listing_exposesClaimFlags() throws Exception {
        Booking freshBooking = withinDeadlineBooking();
        Booking claimedBooking = createBooking(LocalDate.now(), LocalTime.of(16, 0), LocalTime.of(17, 0),
                BookingStatus.COMPLETED);
        Booking oldBooking = expiredBooking();
        String token = login(student);
        mockMvc.perform(postClaim(token, claimedBooking.getBookingId(), "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\"}"))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/bookings/mine")
                        .param("role", Role.STUDENT.name())
                        .param("category", BookingHistoryCategory.COMPLETED.name())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        JsonNode freshItem = findByBookingId(content, freshBooking.getBookingId());
        JsonNode claimedItem = findByBookingId(content, claimedBooking.getBookingId());
        JsonNode oldItem = findByBookingId(content, oldBooking.getBookingId());

        assertThat(freshItem).isNotNull();
        assertThat(freshItem.get("canClaim").asBoolean()).isTrue();
        assertThat(freshItem.get("claimableUntil").isNull()).isFalse();
        assertThat(claimedItem).isNotNull();
        assertThat(claimedItem.get("canClaim").asBoolean()).isFalse();
        assertThat(oldItem).isNotNull();
        assertThat(oldItem.get("canClaim").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("CP-24 - la contraparte reclama la misma sesion: 201 y la retencion no se duplica")
    void create_counterpartyClaimsSameSession_returns201WithoutDuplicatingRetention() throws Exception {
        Booking booking = withinDeadlineBooking();
        heldTransfer(booking);
        String studentToken = login(student);
        MvcResult studentClaim = mockMvc.perform(postClaim(studentToken, booking.getBookingId(),
                        "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String studentClaimId = objectMapper.readTree(studentClaim.getResponse().getContentAsString())
                .get("id").asText();

        String tutorToken = login(tutor);
        mockMvc.perform(postClaim(tutorToken, booking.getBookingId(), "{\"reason\":\"I_COULD_NOT_ATTEND\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimantRole").value("TUTOR"));

        assertThat(claimCount(booking)).isEqualTo(2);
        FundsTransfer transfer = fundsTransferRepository.findByBooking_BookingId(booking.getBookingId()).orElseThrow();
        assertThat(transfer.getFundsStatus()).isEqualTo(FundsStatus.SUSPENDED_BY_CLAIM);
        assertThat(transfer.getBlockingClaimId().toString()).isEqualTo(studentClaimId);
    }

    @Test
    @DisplayName("CP-22 - falla al retener fondos: 500 y el reclamo no persiste")
    void create_ledgerFailure_returns500AndRollsBackClaim() throws Exception {
        Booking booking = withinDeadlineBooking();
        heldTransfer(booking);
        String token = login(student);
        doThrow(new RuntimeException("ledger down"))
                .when(fundsLedgerService).markSuspendedByClaim(any(), any());

        mockMvc.perform(postClaim(token, booking.getBookingId(),
                        "{\"reason\":\"STUDENT_COULD_NOT_ATTEND\"}", pdfFile("a.pdf")))
                .andExpect(status().isInternalServerError());

        assertThat(sessionClaimRepository.existsByBooking_BookingIdAndStatus(
                booking.getBookingId(), ClaimStatus.OPEN)).isFalse();
        assertThat(claimCount(booking)).isZero();
        FundsTransfer unchanged = fundsTransferRepository.findByBooking_BookingId(booking.getBookingId()).orElseThrow();
        assertThat(unchanged.getFundsStatus()).isEqualTo(FundsStatus.HELD);
        assertThat(unchanged.getBlockingClaimId()).isNull();
    }

    @Test
    @DisplayName("CP-23 - doble envio concurrente: solo persiste un reclamo")
    void concurrentCreate_onlyOneClaimPersists() throws Exception {
        Booking booking = withinDeadlineBooking();
        String token = login(student);
        var request = new CreateClaimRequest(ClaimReason.STUDENT_COULD_NOT_ATTEND, null);

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> template.execute(status -> {
                sessionClaimService.create(student.getUserId(), booking.getBookingId(), request, null);
                firstInserted.countDown();
                try {
                    releaseFirst.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return "created";
            }));

            assertThat(firstInserted.await(10, TimeUnit.SECONDS)).isTrue();

            Future<String> second = pool.submit(() -> {
                secondStarted.countDown();
                try {
                    return template.execute(status -> {
                        sessionClaimService.create(student.getUserId(), booking.getBookingId(), request, null);
                        return "created";
                    });
                } catch (DuplicateResourceException ex) {
                    return "duplicate";
                }
            });

            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(300);
            releaseFirst.countDown();

            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo("created");
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo("duplicate");
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
        }

        assertThat(claimCount(booking)).isEqualTo(1);
        assertThat(sessionClaimRepository.existsByBooking_BookingIdAndStatus(
                booking.getBookingId(), ClaimStatus.OPEN)).isTrue();
        mockMvc.perform(get("/api/v1/bookings/{bookingId}/claims/eligibility", booking.getBookingId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockReason").value("ACTIVE_CLAIM_EXISTS"));
    }
}