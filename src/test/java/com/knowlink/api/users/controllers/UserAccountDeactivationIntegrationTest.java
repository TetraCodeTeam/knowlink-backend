package com.knowlink.api.users.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.claims.data.enums.ClaimReason;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.claims.repositories.SessionClaimRepository;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsConcept;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;
import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityBlockRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserAccountDeactivationIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String DEACTIVATE_PATH = "/api/v1/users/me/deactivate";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String ACTIVE_BOOKINGS_MESSAGE =
            "No pod\u00e9s dar de baja tu cuenta con reservas activas pendientes";
    private static final String DEACTIVATED_MESSAGE =
            "Tu cuenta est\u00e1 desactivada. Pod\u00e9s reactivarla para volver a usar KnowLink.";
    private static final String ACTIVE_CLAIMS_MESSAGE =
            "No pod\u00e9s dar de baja tu cuenta con reclamos abiertos";
    private static final String RETAINED_FUNDS_MESSAGE =
            "No pod\u00e9s dar de baja tu cuenta con fondos retenidos pendientes";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IUserRepository userRepository;
    @Autowired
    private IStudentProfileRepository studentProfileRepository;
    @Autowired
    private ITutorProfileRepository tutorProfileRepository;
    @Autowired
    private ITutorSubjectRepository tutorSubjectRepository;
    @Autowired
    private ICareerRepository careerRepository;
    @Autowired
    private ISubjectRepository subjectRepository;
    @Autowired
    private ITimeSlotRepository timeSlotRepository;
    @Autowired
    private IBookingRepository bookingRepository;
    @Autowired
    private IRatingRepository ratingRepository;
    @Autowired
    private IAvailabilityBlockRepository availabilityBlockRepository;
    @Autowired
    private SessionClaimRepository sessionClaimRepository;
    @Autowired
    private IFundsTransferRepository fundsTransferRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Career career;
    private User student;
    private User tutor;
    private TutorProfile tutorProfile;
    private TutorSubject tutorSubject;

    @BeforeEach
    void setUp() {
        career = careerRepository.save(Career.builder()
                .name("Career " + UUID.randomUUID())
                .build());
        Subject subject = subjectRepository.save(Subject.builder()
                .name("Subject " + UUID.randomUUID())
                .isBasic(false)
                .career(career)
                .build());

        student = saveUser("Student Deactivation", Role.STUDENT);
        studentProfileRepository.save(StudentProfile.builder()
                .user(student)
                .career(career)
                .createdAt(LocalDateTime.now())
                .build());

        tutor = saveUser("Tutor Deactivation", Role.TUTOR);
        tutorProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(tutor)
                .career(career)
                .verified(true)
                .build());
        tutorSubject = tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .pricePerHour(new BigDecimal("2000.00"))
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build());
    }

    @Test
    @DisplayName("Deactivating a student returns 204 and the previous JWT is rejected with 401")
    void deactivateStudent_returns204_andRejectsPreviousJwt() throws Exception {
        String token = loginAndGetToken(student.getEmail());

        mockMvc.perform(patch(DEACTIVATE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        User updated = userRepository.findById(student.getUserId()).orElseThrow();
        assertThat(updated.getAccountStatus()).isEqualTo(AccountStatus.DEACTIVATED);
        assertThat(updated.getDeactivatedAt()).isNotNull();

        mockMvc.perform(get("/api/v1/users/{id}", student.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Deactivating a tutor keeps all academic data intact")
    void deactivateTutor_retainsProfilesSubjectsRatingsAndAvailability() throws Exception {
        Booking completedBooking = createBooking(tutorSubject, tutor, student, BookingStatus.COMPLETED,
                LocalDate.of(2026, 10, 10), LocalTime.of(10, 0));
        Rating rating = ratingRepository.save(Rating.builder()
                .booking(completedBooking)
                .ratedUser(tutor)
                .raterUser(student)
                .score(5)
                .comment("Excelente clase.")
                .ratingDate(LocalDateTime.of(2026, 10, 11, 10, 0))
                .visible(true)
                .build());
        AvailabilityBlock block = availabilityBlockRepository.save(AvailabilityBlock.builder()
                .date(LocalDate.of(2026, 12, 1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .tutorProfile(tutorProfile)
                .build());

        String token = loginAndGetToken(tutor.getEmail());

        mockMvc.perform(patch(DEACTIVATE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(tutor.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.DEACTIVATED);
        assertThat(tutorProfileRepository.findById(tutorProfile.getTutorProfileId())).isPresent();
        assertThat(tutorSubjectRepository.findById(tutorSubject.getTutorSubjectId())).isPresent();
        assertThat(ratingRepository.findById(rating.getRatingId())).isPresent();
        assertThat(availabilityBlockRepository.findById(block.getAvailabilityBlockId())).isPresent();
    }

    @Test
    @DisplayName("A student with an active booking cannot deactivate and the account stays ACTIVE")
    void deactivateStudentWithActiveBooking_returns409() throws Exception {
        createBooking(tutorSubject, tutor, student, BookingStatus.PENDING,
                LocalDate.of(2026, 12, 10), LocalTime.of(10, 0));
        String token = loginAndGetToken(student.getEmail());

        mockMvc.perform(patch(DEACTIVATE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("ACCOUNT_HAS_ACTIVE_BOOKINGS"))
                .andExpect(jsonPath("$.message").value(ACTIVE_BOOKINGS_MESSAGE));

        assertThat(userRepository.findById(student.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("A tutor with an active booking cannot deactivate and the account stays ACTIVE")
    void deactivateTutorWithActiveBooking_returns409() throws Exception {
        User otherStudent = saveUser("Other Student", Role.STUDENT);
        createBooking(tutorSubject, tutor, otherStudent, BookingStatus.BOOKED,
                LocalDate.of(2026, 12, 11), LocalTime.of(10, 0));
        String token = loginAndGetToken(tutor.getEmail());

        mockMvc.perform(patch(DEACTIVATE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("ACCOUNT_HAS_ACTIVE_BOOKINGS"))
                .andExpect(jsonPath("$.message").value(ACTIVE_BOOKINGS_MESSAGE));

        assertThat(userRepository.findById(tutor.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("An open claim filed by the student blocks the student account deactivation")
    void deactivateClaimantWithOpenClaim_returns409() throws Exception {
        Booking completed = createBooking(tutorSubject, tutor, student, BookingStatus.COMPLETED,
                LocalDate.of(2026, 10, 12), LocalTime.of(10, 0));
        saveClaim(completed, student, ClaimStatus.OPEN);
        String token = loginAndGetToken(student.getEmail());

        mockMvc.perform(patch(DEACTIVATE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("ACCOUNT_HAS_ACTIVE_CLAIMS"))
                .andExpect(jsonPath("$.message").value(ACTIVE_CLAIMS_MESSAGE));

        assertThat(userRepository.findById(student.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("An open claim blocks the counterpart tutor account deactivation")
    void deactivateTutorWithOpenClaim_returns409() throws Exception {
        Booking completed = createBooking(tutorSubject, tutor, student, BookingStatus.COMPLETED,
                LocalDate.of(2026, 10, 13), LocalTime.of(10, 0));
        saveClaim(completed, student, ClaimStatus.OPEN);
        String token = loginAndGetToken(tutor.getEmail());

        mockMvc.perform(patch(DEACTIVATE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("ACCOUNT_HAS_ACTIVE_CLAIMS"))
                .andExpect(jsonPath("$.message").value(ACTIVE_CLAIMS_MESSAGE));

        assertThat(userRepository.findById(tutor.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Retained funds from a completed session block deactivation")
    void deactivateWithRetainedFunds_returns409() throws Exception {
        Booking completed = createBooking(tutorSubject, tutor, student, BookingStatus.COMPLETED,
                LocalDate.of(2026, 10, 14), LocalTime.of(10, 0));
        fundsTransferRepository.save(FundsTransfer.builder()
                .booking(completed)
                .originalAmount(new BigDecimal("2100.00"))
                .transferredAmount(BigDecimal.ZERO)
                .recipient(FundsRecipient.TUTOR)
                .concept(FundsConcept.HELD.label())
                .fundsStatus(FundsStatus.SUSPENDED_BY_CLAIM)
                .build());
        String token = loginAndGetToken(student.getEmail());

        mockMvc.perform(patch(DEACTIVATE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("ACCOUNT_HAS_RETAINED_FUNDS"))
                .andExpect(jsonPath("$.message").value(RETAINED_FUNDS_MESSAGE));

        assertThat(userRepository.findById(student.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Resolved claims and released funds do not block deactivation")
    void deactivateWithResolvedClaimAndReleasedFunds_returns204() throws Exception {
        Booking completed = createBooking(tutorSubject, tutor, student, BookingStatus.COMPLETED,
                LocalDate.of(2026, 10, 15), LocalTime.of(10, 0));
        SessionClaim resolved = saveClaim(completed, student, ClaimStatus.RESOLVED);
        resolved.setResolvedAt(LocalDateTime.now());
        sessionClaimRepository.save(resolved);
        fundsTransferRepository.save(FundsTransfer.builder()
                .booking(completed)
                .originalAmount(new BigDecimal("2100.00"))
                .transferredAmount(new BigDecimal("2037.00"))
                .recipient(FundsRecipient.TUTOR)
                .concept(FundsConcept.RELEASED_SESSION_COMPLETED.label())
                .fundsStatus(FundsStatus.RELEASED_TO_TUTOR)
                .build());
        String token = loginAndGetToken(student.getEmail());

        mockMvc.perform(patch(DEACTIVATE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(student.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.DEACTIVATED);
    }

    @Test
    @DisplayName("An ADMIN cannot deactivate through this endpoint (403)")
    void deactivateAsAdmin_returns403() throws Exception {
        User admin = saveUser("Admin Deactivation", Role.ADMIN);

        mockMvc.perform(patch(DEACTIVATE_PATH).with(auth(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("ACCESS_DENIED"));

        assertThat(userRepository.findById(admin.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Deactivating without a token returns 401")
    void deactivateWithoutToken_returns401() throws Exception {
        mockMvc.perform(patch(DEACTIVATE_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login of a DEACTIVATED account with the right password returns 403 ACCOUNT_DEACTIVATED")
    void loginDeactivated_withCorrectPassword_returns403() throws Exception {
        userRepository.save(withStatus(student, AccountStatus.DEACTIVATED));

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(student.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("ACCOUNT_DEACTIVATED"))
                .andExpect(jsonPath("$.message").value(DEACTIVATED_MESSAGE));
    }

    @Test
    @DisplayName("Login of a DEACTIVATED account with a wrong password returns 401 and hides the state")
    void loginDeactivated_withWrongPassword_returns401() throws Exception {
        userRepository.save(withStatus(student, AccountStatus.DEACTIVATED));

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(student.getEmail(), "WrongPassword123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.detail").value("The email or password is incorrect"));
    }

    @Test
    @DisplayName("Login of an INACTIVE account keeps the previous unverified-account behavior")
    void loginInactive_keepsPreviousBehavior() throws Exception {
        userRepository.save(withStatus(student, AccountStatus.INACTIVE));

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(student.getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Account not active"))
                .andExpect(jsonPath("$.detail").value("You must confirm your account before logging in"));
    }

    @Test
    @DisplayName("GET /users/{id} hides a deactivated account from other students but not from ADMIN")
    void getUserById_hidesDeactivatedAccountFromStudents() throws Exception {
        User deactivated = saveUser("Deactivated Target", Role.STUDENT);
        userRepository.save(withStatus(deactivated, AccountStatus.DEACTIVATED));
        User viewer = saveUser("Curious Student", Role.STUDENT);
        User admin = saveUser("Admin Viewer", Role.ADMIN);

        mockMvc.perform(get("/api/v1/users/{id}", deactivated.getUserId()).with(auth(viewer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("USER_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/users/{id}", deactivated.getUserId()).with(auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(deactivated.getUserId().toString()));
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

    private User withStatus(User user, AccountStatus status) {
        user.setAccountStatus(status);
        return user;
    }

    private String loginAndGetToken(String email) throws Exception {
        String response = mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String loginBody(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    private SessionClaim saveClaim(Booking booking, User claimant, ClaimStatus status) {
        return sessionClaimRepository.save(SessionClaim.builder()
                .booking(booking)
                .claimant(claimant)
                .claimantRole(Role.STUDENT)
                .reason(ClaimReason.TUTOR_COULD_NOT_ATTEND)
                .status(status)
                .activeKey(status == ClaimStatus.OPEN
                        ? booking.getBookingId() + ":" + claimant.getUserId()
                        : null)
                .build());
    }

    private RequestPostProcessor auth(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Booking createBooking(TutorSubject subject, User bookingTutor, User bookingStudent,
            BookingStatus status, LocalDate date, LocalTime startTime) {
        LocalTime endTime = startTime.plusHours(1);
        TimeSlot slot = timeSlotRepository.save(TimeSlot.builder()
                .date(date)
                .startTime(startTime)
                .endTime(endTime)
                .status(SlotStatus.OCCUPIED)
                .tutorProfileId(subject.getTutorProfile().getTutorProfileId())
                .build());
        return bookingRepository.save(Booking.builder()
                .amount(new BigDecimal("2100.00"))
                .sessionDate(date)
                .startTime(startTime)
                .endTime(endTime)
                .topic("Sesion de prueba")
                .modality(Modality.VIRTUAL)
                .bookingStatus(status)
                .timeSlot(slot)
                .tutorSubject(subject)
                .student(bookingStudent)
                .tutor(bookingTutor)
                .build());
    }
}