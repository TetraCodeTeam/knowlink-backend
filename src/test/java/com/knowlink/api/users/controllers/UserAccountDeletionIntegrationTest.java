package com.knowlink.api.users.controllers;

import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.testsupport.CatalogFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.data.models.Hold;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.repositories.IHoldRepository;
import com.knowlink.api.claims.data.enums.ClaimReason;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.claims.repositories.SessionClaimRepository;
import com.knowlink.api.community.data.enums.PostStatus;
import com.knowlink.api.community.data.enums.PostType;
import com.knowlink.api.community.data.models.CommunityAttachment;
import com.knowlink.api.community.data.models.CommunityPost;
import com.knowlink.api.community.data.models.CommunityReply;
import com.knowlink.api.community.data.models.CommunityReplyHelpful;
import com.knowlink.api.community.data.models.TutorRankingEntry;
import com.knowlink.api.community.repositories.ICommunityPostRepository;
import com.knowlink.api.community.repositories.ICommunityReplyHelpfulRepository;
import com.knowlink.api.community.repositories.ICommunityReplyRepository;
import com.knowlink.api.community.repositories.ITutorRankingRepository;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.data.models.Notification;
import com.knowlink.api.notifications.repositories.NotificationRepository;
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
import com.knowlink.api.users.data.enums.TokenType;
import com.knowlink.api.users.data.models.Token;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.ITokenRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserAccountDeletionIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String DELETE_PATH = "/api/v1/users/me/delete-account";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REGISTER_STUDENT_PATH = "/api/v1/auth/register/student";
    private static final String ACTIVE_BOOKINGS_MESSAGE =
            "No pod\u00e9s dar de baja tu cuenta con reservas activas pendientes";
    private static final String PENDING_OBLIGATIONS_MESSAGE =
            "No pod\u00e9s dar de baja tu cuenta con pagos o reclamos pendientes de resoluci\u00f3n.";
    private static final String DELETED_DISPLAY_NAME = "Usuario eliminado";
    private static final AtomicInteger DNI_SEQUENCE = new AtomicInteger(20_000_000);

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
    private IInstitutionRepository institutionRepository;

    private Institution institution;
    @Autowired
    private ISubjectRepository subjectRepository;
    @Autowired
    private ITimeSlotRepository timeSlotRepository;
    @Autowired
    private IBookingRepository bookingRepository;
    @Autowired
    private IHoldRepository holdRepository;
    @Autowired
    private IRatingRepository ratingRepository;
    @Autowired
    private SessionClaimRepository sessionClaimRepository;
    @Autowired
    private IFundsTransferRepository fundsTransferRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ITokenRepository tokenRepository;
    @Autowired
    private ICommunityPostRepository communityPostRepository;
    @Autowired
    private ICommunityReplyRepository communityReplyRepository;
    @Autowired
    private ICommunityReplyHelpfulRepository helpfulRepository;
    @Autowired
    private ITutorRankingRepository rankingRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Career career;
    private Subject subject;
    private User student;
    private User tutor;
    private StudentProfile studentProfile;
    private TutorProfile tutorProfile;
    private TutorSubject tutorSubject;

    @BeforeEach
    void setUp() {
        institution = CatalogFixtures.saveInstitution(institutionRepository, "Test " + System.nanoTime());
        career = careerRepository.save(Career.builder().institution(institution)
                .name("Career " + UUID.randomUUID())
                .build());
        subject = subjectRepository.save(Subject.builder().institution(institution)
                .name("Subject " + UUID.randomUUID())
                
                .career(career)
                .build());

        student = saveUser("Student Deletion", Role.STUDENT);
        studentProfile = studentProfileRepository.save(StudentProfile.builder()
                .user(student)
                .career(career)
                .profilePictureUrl("https://cdn.test/student.png")
                .institutionalId("LEG-123")
                .build());

        tutor = saveUser("Tutor Deletion", Role.TUTOR);
        tutorProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(tutor)
                .career(career)
                .biography("Biografia del tutor")
                .profilePictureUrl("https://cdn.test/tutor.png")
                .institutionalId("DOC-456")
                .address("Calle 123")
                .mercadoPagoLinked(true)
                .verified(true)
                .build());
        tutorSubject = tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .description("Descripcion de la materia")
                .pricePerHour(new BigDecimal("2000.00"))
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build());
    }
    @Test
    @DisplayName("Deleting a student returns 204, anonymizes the row and frees email and DNI")
    void deleteStudent_anonymizesAndFreesCredentials() throws Exception {
        String originalEmail = student.getEmail();
        String originalDni = student.getDni();
        String originalPasswordHash = student.getPassword();
        UUID studentId = student.getUserId();
        String token = loginAndGetToken(originalEmail);

        mockMvc.perform(post(DELETE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isNoContent());

        User deleted = userRepository.findById(studentId).orElseThrow();
        assertThat(deleted.getAccountStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getEmail()).isEqualTo("deleted+" + studentId + "@knowlink.invalid");
        assertThat(deleted.getPassword()).isNotEqualTo(originalPasswordHash);
        assertThat(passwordEncoder.matches(PASSWORD, deleted.getPassword())).isFalse();
        assertThat(deleted.getFirstName()).isNull();
        assertThat(deleted.getLastName()).isNull();
        assertThat(deleted.getDni()).isNull();
        assertThat(deleted.getPhoneNumber()).isNull();
        assertThat(deleted.getProfilePictureUrl()).isNull();
        assertThat(deleted.getFullName()).isEqualTo(DELETED_DISPLAY_NAME);

        StudentProfile clearedProfile = studentProfileRepository
                .findById(studentProfile.getStudentProfileId()).orElseThrow();
        assertThat(clearedProfile.getProfilePictureUrl()).isNull();
        assertThat(clearedProfile.getInstitutionalId()).isNull();

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(originalEmail, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("The email or password is incorrect"));

        mockMvc.perform(get("/api/v1/users/{id}", studentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        User admin = saveUser("Admin Viewer", Role.ADMIN);
        mockMvc.perform(get("/api/v1/users/{id}", studentId).with(auth(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("USER_NOT_FOUND"));

        mockMvc.perform(post(REGISTER_STUDENT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(originalEmail, originalDni)))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmail(originalEmail)).isPresent();
    }

    @Test
    @DisplayName("A wrong password returns 401 and leaves the account untouched")
    void deleteWithWrongPassword_returns401_andKeepsAccount() throws Exception {
        String originalEmail = student.getEmail();
        String originalDni = student.getDni();
        String originalFirstName = student.getFirstName();
        String token = loginAndGetToken(originalEmail);

        mockMvc.perform(post(DELETE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody("WrongPassword123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.detail").value("The email or password is incorrect"));

        User unchanged = userRepository.findById(student.getUserId()).orElseThrow();
        assertThat(unchanged.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(unchanged.getEmail()).isEqualTo(originalEmail);
        assertThat(unchanged.getDni()).isEqualTo(originalDni);
        assertThat(unchanged.getFirstName()).isEqualTo(originalFirstName);
        assertThat(unchanged.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("A student with an active booking cannot delete the account")
    void deleteStudentWithActiveBooking_returns409() throws Exception {
        createBooking(tutorSubject, tutor, student, BookingStatus.PENDING,
                LocalDate.of(2026, 12, 10), LocalTime.of(10, 0));
        String originalEmail = student.getEmail();
        String token = loginAndGetToken(originalEmail);

        mockMvc.perform(post(DELETE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("ACCOUNT_HAS_ACTIVE_BOOKINGS"))
                .andExpect(jsonPath("$.message").value(ACTIVE_BOOKINGS_MESSAGE));

        User unchanged = userRepository.findById(student.getUserId()).orElseThrow();
        assertThat(unchanged.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(unchanged.getEmail()).isEqualTo(originalEmail);
    }

    @Test
    @DisplayName("A tutor with an active booking as tutor cannot delete the account")
    void deleteTutorWithActiveBooking_returns409() throws Exception {
        User otherStudent = saveUser("Other Student", Role.STUDENT);
        createBooking(tutorSubject, tutor, otherStudent, BookingStatus.BOOKED,
                LocalDate.of(2026, 12, 11), LocalTime.of(10, 0));
        String originalEmail = tutor.getEmail();
        String token = loginAndGetToken(originalEmail);

        mockMvc.perform(post(DELETE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("ACCOUNT_HAS_ACTIVE_BOOKINGS"))
                .andExpect(jsonPath("$.message").value(ACTIVE_BOOKINGS_MESSAGE));

        User unchanged = userRepository.findById(tutor.getUserId()).orElseThrow();
        assertThat(unchanged.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(unchanged.getEmail()).isEqualTo(originalEmail);
    }

    @Test
    @DisplayName("Only finished bookings and expired holds do not block the deletion")
    void deleteWithFinishedBookings_returns204() throws Exception {
        createBooking(tutorSubject, tutor, student, BookingStatus.COMPLETED,
                LocalDate.of(2026, 10, 10), LocalTime.of(10, 0));
        createBooking(tutorSubject, tutor, student, BookingStatus.CANCELLED,
                LocalDate.of(2026, 10, 11), LocalTime.of(10, 0));
        createBooking(tutorSubject, tutor, student, BookingStatus.EXPIRED,
                LocalDate.of(2026, 10, 12), LocalTime.of(10, 0));
        saveExpiredHold(student);
        String token = loginAndGetToken(student.getEmail());

        mockMvc.perform(post(DELETE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(student.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.DELETED);
        assertThat(bookingRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("An open claim blocks the deletion with ACCOUNT_HAS_PENDING_OBLIGATIONS")
    void deleteWithOpenClaim_returns409PendingObligations() throws Exception {
        Booking completed = createBooking(tutorSubject, tutor, student, BookingStatus.COMPLETED,
                LocalDate.of(2026, 10, 13), LocalTime.of(10, 0));
        saveClaim(completed, student, ClaimStatus.OPEN);
        String originalEmail = student.getEmail();
        String token = loginAndGetToken(originalEmail);

        mockMvc.perform(post(DELETE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("ACCOUNT_HAS_PENDING_OBLIGATIONS"))
                .andExpect(jsonPath("$.message").value(PENDING_OBLIGATIONS_MESSAGE));

        User unchanged = userRepository.findById(student.getUserId()).orElseThrow();
        assertThat(unchanged.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(unchanged.getEmail()).isEqualTo(originalEmail);
    }

    @Test
    @DisplayName("Retained funds block the deletion with ACCOUNT_HAS_PENDING_OBLIGATIONS")
    void deleteWithRetainedFunds_returns409PendingObligations() throws Exception {
        Booking completed = createBooking(tutorSubject, tutor, student, BookingStatus.COMPLETED,
                LocalDate.of(2026, 10, 14), LocalTime.of(10, 0));
        fundsTransferRepository.save(FundsTransfer.builder()
                .booking(completed)
                .originalAmount(new BigDecimal("2100.00"))
                .transferredAmount(BigDecimal.ZERO)
                .recipient(FundsRecipient.TUTOR)
                .concept(FundsConcept.HELD.label())
                .fundsStatus(FundsStatus.HELD)
                .build());
        String token = loginAndGetToken(student.getEmail());

        mockMvc.perform(post(DELETE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("ACCOUNT_HAS_PENDING_OBLIGATIONS"))
                .andExpect(jsonPath("$.message").value(PENDING_OBLIGATIONS_MESSAGE));

        assertThat(userRepository.findById(student.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }
    @Test
    @DisplayName("An ADMIN cannot delete through this endpoint (403)")
    void deleteAsAdmin_returns403() throws Exception {
        User admin = saveUser("Admin Deletion", Role.ADMIN);

        mockMvc.perform(post(DELETE_PATH)
                        .with(auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("ACCESS_DENIED"));

        assertThat(userRepository.findById(admin.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Deleting without a token returns 401 and a blank password returns 400")
    void deleteWithoutToken_returns401_andBlankPasswordReturns400() throws Exception {
        mockMvc.perform(post(DELETE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(DELETE_PATH)
                        .with(auth(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody("")))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(student.getUserId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Deleting a tutor clears the profile, subjects and ranking snapshot")
    void deleteTutor_clearsProfileSubjectsAndRanking() throws Exception {
        TutorRankingEntry rankingEntry = rankingRepository.save(TutorRankingEntry.builder()
                .periodStart(LocalDate.now().withDayOfMonth(1))
                .tutor(tutor)
                .position(1)
                .score(10.0)
                .bestAnswers(2)
                .helpfulMarks(3)
                .reviewsCount(4)
                .completedClasses(5)
                .build());
        String token = loginAndGetToken(tutor.getEmail());

        mockMvc.perform(post(DELETE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isNoContent());

        TutorProfile clearedProfile = tutorProfileRepository.findById(tutorProfile.getTutorProfileId())
                .orElseThrow();
        assertThat(clearedProfile.getBiography()).isNull();
        assertThat(clearedProfile.getProfilePictureUrl()).isNull();
        assertThat(clearedProfile.getInstitutionalId()).isNull();
        assertThat(clearedProfile.getAddress()).isNull();
        assertThat(clearedProfile.isMercadoPagoLinked()).isFalse();
        assertThat(clearedProfile.isVerified()).isFalse();

        TutorSubject clearedSubject = tutorSubjectRepository.findById(tutorSubject.getTutorSubjectId())
                .orElseThrow();
        assertThat(clearedSubject.getDescription()).isNull();
        assertThat(clearedSubject.isActive()).isFalse();

        assertThat(rankingRepository.findById(rankingEntry.getRankingEntryId())).isEmpty();
    }

    @Test
    @DisplayName("Deleting removes notifications, tokens, holds, community content and helpful marks")
    void delete_removesTransientDataAndCascadesCommunityContent() throws Exception {
        saveExpiredHold(student);
        saveNotification(student, "deletion-notification-1");
        saveToken(student, TokenType.ACCOUNT_CONFIRMATION);
        saveToken(student, TokenType.PASSWORD_RESET);

        CommunityPost post = communityPostRepository.save(CommunityPost.builder()
                .author(student)
                .authorRole(Role.STUDENT)
                .type(PostType.HELP_REQUEST)
                .status(PostStatus.OPEN)
                .subject(subject)
                .title("Consulta sobre la materia")
                .content("Contenido de la consulta")
                .attachment(CommunityAttachment.builder()
                        .storagePath("community/student-post.png")
                        .originalFileName("post.png")
                        .contentType("image/png")
                        .sizeInBytes(10L)
                        .build())
                .build());
        CommunityReply tutorReply = communityReplyRepository.save(CommunityReply.builder()
                .post(post)
                .author(tutor)
                .authorRole(Role.TUTOR)
                .content("Respuesta del tutor")
                .includeBookingCard(false)
                .build());
        CommunityReply studentReply = communityReplyRepository.save(CommunityReply.builder()
                .post(post)
                .author(student)
                .authorRole(Role.STUDENT)
                .content("Respuesta del alumno")
                .includeBookingCard(false)
                .build());
        CommunityReplyHelpful helpful = helpfulRepository.save(CommunityReplyHelpful.builder()
                .reply(tutorReply)
                .user(student)
                .build());
        String token = loginAndGetToken(student.getEmail());

        mockMvc.perform(post(DELETE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(holdRepository.findByStudent_UserId(student.getUserId())).isEmpty();
        assertThat(notificationRepository.findAll())
                .noneMatch(notification -> notification.getRecipient().getUserId().equals(student.getUserId()));
        assertThat(tokenRepository.findAll())
                .noneMatch(savedToken -> savedToken.getUser().getUserId().equals(student.getUserId()));

        assertThat(communityPostRepository.findById(post.getPostId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(communityPostRepository.findByPostIdAndDeletedAtIsNull(post.getPostId())).isEmpty();
        assertThat(communityReplyRepository.findById(studentReply.getReplyId()).orElseThrow().getDeletedAt())
                .isNotNull();
        assertThat(communityReplyRepository.findById(tutorReply.getReplyId()).orElseThrow().getDeletedAt())
                .isNull();
        assertThat(helpfulRepository.findById(helpful.getHelpfulId())).isEmpty();
    }

    @Test
    @DisplayName("Booking, funds and rating history survive the deletion")
    void delete_preservesBookingFundsAndRatingHistory() throws Exception {
        Booking completed = createBooking(tutorSubject, tutor, student, BookingStatus.COMPLETED,
                LocalDate.of(2026, 10, 15), LocalTime.of(10, 0));
        FundsTransfer funds = fundsTransferRepository.save(FundsTransfer.builder()
                .booking(completed)
                .originalAmount(new BigDecimal("2100.00"))
                .transferredAmount(new BigDecimal("2037.00"))
                .recipient(FundsRecipient.TUTOR)
                .concept(FundsConcept.RELEASED_SESSION_COMPLETED.label())
                .fundsStatus(FundsStatus.RELEASED_TO_TUTOR)
                .build());
        Rating rating = ratingRepository.save(Rating.builder()
                .booking(completed)
                .ratedUser(tutor)
                .raterUser(student)
                .score(5)
                .comment("Excelente clase")
                .ratingDate(LocalDateTime.of(2026, 10, 16, 10, 0))
                .visible(true)
                .build());
        String token = loginAndGetToken(student.getEmail());

        mockMvc.perform(post(DELETE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(bookingRepository.findById(completed.getBookingId())).isPresent();
        assertThat(fundsTransferRepository.findById(funds.getFundsTransferId())).isPresent();
        assertThat(ratingRepository.findById(rating.getRatingId())).isPresent();
    }

    @Test
    @DisplayName("A second deletion request with the old JWT returns 401, never 500")
    void deleteTwice_returns401OnSecondAttempt() throws Exception {
        String token = loginAndGetToken(student.getEmail());

        mockMvc.perform(post(DELETE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(DELETE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    private User saveUser(String fullName, Role role) {
        return userRepository.save(User.builder()
                .fullName(fullName)
                .firstName(fullName.split(" ")[0])
                .lastName("Apellido")
                .email(fullName.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID() + "@test.com")
                .password(passwordEncoder.encode(PASSWORD))
                .dni(String.valueOf(DNI_SEQUENCE.getAndIncrement()))
                .phoneNumber("+5491100000000")
                .profilePictureUrl("https://cdn.test/" + UUID.randomUUID() + ".png")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
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

    private String passwordBody(String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("password", password));
    }

    private String registrationBody(String email, String dni) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", PASSWORD,
                "confirmPassword", PASSWORD,
                "firstName", "Nuevo",
                "lastName", "Usuario",
                "dni", dni,
                "phoneNumber", "+5491100000001",
                "career", career.getName(),
                "institutionId", institution.getInstitutionId()));
    }

    private RequestPostProcessor auth(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
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

    private Notification saveNotification(User user, String dedupeKey) {
        return notificationRepository.save(Notification.builder()
                .recipient(user)
                .recipientRole(user.getRole())
                .type(NotificationType.SESSION_COMPLETED)
                .message("Mensaje de prueba")
                .dedupeKey(dedupeKey)
                .build());
    }

    private Token saveToken(User user, TokenType tokenType) {
        return tokenRepository.save(Token.builder()
                .user(user)
                .tokenType(tokenType)
                .createdAt(LocalDateTime.now().minusHours(1))
                .tokenExpirationDate(LocalDateTime.now().plusHours(1))
                .build());
    }

    private Hold saveExpiredHold(User studentUser) {
        LocalDate date = LocalDate.of(2027, 1, 15);
        TimeSlot slot = timeSlotRepository.save(TimeSlot.builder()
                .date(date)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .status(SlotStatus.AVAILABLE)
                .tutorProfileId(tutorProfile.getTutorProfileId())
                .build());
        return holdRepository.save(Hold.builder()
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .expiresAt(LocalDateTime.now().minusMinutes(5))
                .timeSlot(slot)
                .student(studentUser)
                .build());
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