package com.knowlink.api.notifications.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.events.BookingCreatedEvent;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.data.models.Notification;
import com.knowlink.api.notifications.listeners.BookingNotificationListener;
import com.knowlink.api.notifications.repositories.NotificationRepository;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerIntegrationTest {

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
    private NotificationRepository notificationRepository;

    @Autowired
    private INotificationService notificationService;

    @Autowired
    private BookingNotificationListener bookingNotificationListener;

    @Autowired
    private ICareerRepository careerRepository;

    @Autowired
    private IStudentProfileRepository studentProfileRepository;

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

    private Career career;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        career = careerRepository.save(Career.builder()
                .name("Career " + UUID.randomUUID()).build());
    }

    @Test
    @DisplayName("CP-01 - listado ordenado, unread-count, marcar leída e idempotencia (CA 1, 2, 3 y 5)")
    void listUnreadCountAndMarkAsRead_flow() throws Exception {
        User student = saveUser("Student Flow", Role.STUDENT, AccountStatus.ACTIVE);
        LocalDateTime base = LocalDateTime.of(2026, 10, 20, 10, 0);
        seedNotification(student, NotificationType.BOOKING_CONFIRMED, "Primera", base, null);
        Notification second = seedNotification(student, NotificationType.SESSION_COMPLETED, "Segunda",
                base.plusHours(1), null);
        seedNotification(student, NotificationType.FUNDS_TRANSFERRED, "Tercera", base.plusHours(2), null);
        String token = login(student);

        mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].message").value("Tercera"))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.content[0].type").value(NotificationType.FUNDS_TRANSFERRED.name()))
                .andExpect(jsonPath("$.content[2].message").value("Primera"));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));

        mockMvc.perform(get("/api/v1/notifications?size=100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));

        mockMvc.perform(get("/api/v1/notifications?size=0")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", second.getNotificationId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        LocalDateTime firstReadAt = reload(second).getReadAt();
        assertThat(firstReadAt).isNotNull();

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", second.getNotificationId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        assertThat(reload(second).getReadAt()).isEqualTo(firstReadAt);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        mockMvc.perform(get("/api/v1/notifications?unreadOnly=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("CP-02 - aislamiento: la notificacion de otro usuario da 404 y no se modifica")
    void markAsRead_ofAnotherUser_returns404AndDoesNotModifyIt() throws Exception {
        User alice = saveUser("Student Alice", Role.STUDENT, AccountStatus.ACTIVE);
        User bob = saveUser("Student Bob", Role.STUDENT, AccountStatus.ACTIVE);
        Notification fromBob = seedNotification(bob, NotificationType.BOOKING_CONFIRMED, "De Bob",
                LocalDateTime.of(2026, 10, 20, 9, 0), null);
        seedNotification(alice, NotificationType.BOOKING_CONFIRMED, "De Alicia",
                LocalDateTime.of(2026, 10, 20, 9, 1), null);
        String aliceToken = login(alice);

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].message").value("De Alicia"));

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", fromBob.getNotificationId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("NOTIFICATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("La notificación no existe."));

        assertThat(reload(fromBob).getReadAt()).isNull();
    }

    @Test
    @DisplayName("CP-03 - reserva creada: ambas partes ven BOOKING_CONFIRMED y el reintento no duplica (CA 6)")
    void bookingCreated_showsBookingConfirmedForBothParties() throws Exception {
        User student = saveUser("Student Booking", Role.STUDENT, AccountStatus.ACTIVE);
        User tutor = saveUser("Tutor Booking", Role.TUTOR, AccountStatus.ACTIVE);
        Booking booking = createBooking(student, tutor);
        BookingCreatedEvent event = new BookingCreatedEvent(booking.getBookingId());

        bookingNotificationListener.handleBookingCreated(event);

        String studentToken = login(student);
        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value(NotificationType.BOOKING_CONFIRMED.name()))
                .andExpect(jsonPath("$.content[0].referenceId").value(booking.getBookingId().toString()))
                .andExpect(jsonPath("$.content[0].message").value(
                        "Tu reserva con Tutor Booking para el "
                                + booking.getSessionDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                + " a las 15:00 fue confirmada."));

        String tutorToken = login(tutor);
        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value(NotificationType.BOOKING_CONFIRMED.name()))
                .andExpect(jsonPath("$.content[0].message", containsString("reservó una clase")));

        bookingNotificationListener.handleBookingCreated(event);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("CP-04 - notifyAdmins: cada admin activo recibe su propia notificacion (CA 7)")
    void notifyAdmins_notifiesEveryActiveAdminOnly() {
        User activeAdminOne = saveUser("Admin One", Role.ADMIN, AccountStatus.ACTIVE);
        User activeAdminTwo = saveUser("Admin Two", Role.ADMIN, AccountStatus.ACTIVE);
        User inactiveAdmin = saveUser("Admin Inactive", Role.ADMIN, AccountStatus.INACTIVE);
        String dedupeKey = "ADMIN_VALIDATION_REQUEST_PENDING:" + UUID.randomUUID();

        notificationService.notifyAdmins(NotificationType.ADMIN_VALIDATION_REQUEST_PENDING,
                "Hay una solicitud de validación pendiente.", null, dedupeKey);

        assertThat(notificationRepository.countByRecipient_UserIdAndReadAtIsNull(activeAdminOne.getUserId()))
                .isEqualTo(1L);
        assertThat(notificationRepository.countByRecipient_UserIdAndReadAtIsNull(activeAdminTwo.getUserId()))
                .isEqualTo(1L);
        assertThat(notificationRepository.countByRecipient_UserIdAndReadAtIsNull(inactiveAdmin.getUserId()))
                .isZero();
    }

    private Notification seedNotification(User recipient, NotificationType type, String message,
            LocalDateTime createdAt, LocalDateTime readAt) {
        return notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(type)
                .message(message)
                .resourceId(UUID.randomUUID())
                .dedupeKey(UUID.randomUUID().toString())
                .createdAt(createdAt)
                .readAt(readAt)
                .build());
    }

    private Notification reload(Notification notification) {
        return notificationRepository.findById(notification.getNotificationId()).orElseThrow();
    }

    private User saveUser(String fullName, Role role, AccountStatus accountStatus) {
        User user = userRepository.save(User.builder()
                .fullName(fullName)
                .email(fullName.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID() + "@test.com")
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .accountStatus(accountStatus)
                .build());
        if (role == Role.STUDENT) {
            studentProfileRepository.save(StudentProfile.builder().user(user).career(career).build());
        }
        if (role == Role.TUTOR) {
            tutorProfileRepository.save(TutorProfile.builder().user(user).career(career).verified(true).build());
        }
        return user;
    }

    private Booking createBooking(User student, User tutor) {
        TutorProfile tutorProfile = tutorProfileRepository.findByUserId(tutor.getUserId()).orElseThrow();
        Subject subject = subjectRepository.save(Subject.builder()
                .name("Subject " + UUID.randomUUID()).isBasic(true).career(career).build());
        TutorSubject tutorSubject = tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .pricePerHour(new BigDecimal("2000.00"))
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.BOTH)
                .build());
        LocalDate sessionDate = LocalDate.now().plusDays(1);
        TimeSlot slot = timeSlotRepository.save(TimeSlot.builder()
                .date(sessionDate)
                .startTime(LocalTime.of(15, 0))
                .endTime(LocalTime.of(16, 0))
                .status(SlotStatus.OCCUPIED)
                .tutorProfileId(tutorProfile.getTutorProfileId())
                .build());
        return bookingRepository.save(Booking.builder()
                .amount(new BigDecimal("2100.00"))
                .sessionDate(sessionDate)
                .startTime(LocalTime.of(15, 0))
                .endTime(LocalTime.of(16, 0))
                .topic("Notification topic")
                .modality(Modality.VIRTUAL)
                .bookingStatus(BookingStatus.BOOKED)
                .timeSlot(slot)
                .tutorSubject(tutorSubject)
                .student(student)
                .tutor(tutor)
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

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}