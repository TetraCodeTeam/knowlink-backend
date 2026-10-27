package com.knowlink.api.notifications.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.bookings.controllers.requests.CreateBookingRequest;
import com.knowlink.api.bookings.data.models.Hold;
import com.knowlink.api.bookings.repositories.IHoldRepository;
import com.knowlink.api.bookings.services.interfaces.IBookingService;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.data.models.Notification;
import com.knowlink.api.notifications.repositories.NotificationRepository;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.utils.AppTimeZone;
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
    private IBookingService bookingService;

    @Autowired
    private IHoldRepository holdRepository;

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
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.otherRoleCount").value(0));

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
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.otherRoleCount").value(0));

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
    @DisplayName("CP-03 - reserva creada por el flujo real: ambas partes ven BOOKING_CONFIRMED (CA 6)")
    void bookingCreated_showsBookingConfirmedForBothParties() throws Exception {
        User student = saveUser("Student Booking", Role.STUDENT, AccountStatus.ACTIVE);
        User tutor = saveUser("Tutor Booking", Role.TUTOR, AccountStatus.ACTIVE);
        CreatedBooking booking = createBookingThroughService(student, tutor);
        awaitBookingConfirmedNotifications(booking.bookingId());

        String studentToken = login(student);
        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value(NotificationType.BOOKING_CONFIRMED.name()))
                .andExpect(jsonPath("$.content[0].referenceId").value(booking.bookingId().toString()))
                .andExpect(jsonPath("$.content[0].title").value("Reserva confirmada"))
                .andExpect(jsonPath("$.content[0].message").value(
                        "Reservaste " + booking.subjectName() + " - "
                                + com.knowlink.api.notifications.utils.NotificationTextFormats
                                        .dateWithWeekday(booking.sessionDate())
                                + " a las 15:00."));

        String tutorToken = login(tutor);
        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value(NotificationType.BOOKING_CONFIRMED.name()))
                .andExpect(jsonPath("$.content[0].title").value("Nueva reserva confirmada"))
                .andExpect(jsonPath("$.content[0].message", containsString("reservó " + booking.subjectName())));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.otherRoleCount").value(0));

        assertThat(notificationRepository.countByRecipient_UserIdAndRecipientRole(
                student.getUserId(), Role.STUDENT)).isEqualTo(1);
        assertThat(notificationRepository.countByRecipient_UserIdAndRecipientRole(
                tutor.getUserId(), Role.TUTOR)).isEqualTo(1);
    }

    @Test
    @DisplayName("CP-04 - notifyAdmins: cada admin activo recibe su propia notificacion (CA 7)")
    void notifyAdmins_notifiesEveryActiveAdminOnly() {
        User activeAdminOne = saveUser("Admin One", Role.ADMIN, AccountStatus.ACTIVE);
        User activeAdminTwo = saveUser("Admin Two", Role.ADMIN, AccountStatus.ACTIVE);
        User inactiveAdmin = saveUser("Admin Inactive", Role.ADMIN, AccountStatus.INACTIVE);
        String dedupeKey = "ADMIN_VALIDATION_REQUEST_PENDING:" + UUID.randomUUID();

        notificationService.notifyAdmins(NotificationType.ADMIN_VALIDATION_REQUEST_PENDING, "Validación pendiente",
                "Hay una solicitud de validación pendiente.", null, dedupeKey);

        assertThat(notificationRepository.countUnreadVisibleByRecipient(
                activeAdminOne.getUserId(), Role.ADMIN, LocalDateTime.now())).isEqualTo(1L);
        assertThat(notificationRepository.countUnreadVisibleByRecipient(
                activeAdminTwo.getUserId(), Role.ADMIN, LocalDateTime.now())).isEqualTo(1L);
        assertThat(notificationRepository.countUnreadVisibleByRecipient(
                inactiveAdmin.getUserId(), Role.ADMIN, LocalDateTime.now())).isZero();
    }

    @Test
    @DisplayName("CP-05 - filtro status: READ y UNREAD devuelven conjuntos disjuntos (CA 8)")
    void listStatusFilter_returnsDisjointSets() throws Exception {
        User student = saveUser("Student Filter", Role.STUDENT, AccountStatus.ACTIVE);
        LocalDateTime base = LocalDateTime.of(2026, 10, 20, 10, 0);
        seedNotification(student, NotificationType.BOOKING_CONFIRMED, "Sin leer", base, null);
        seedNotification(student, NotificationType.SESSION_COMPLETED, "Leida", base.plusHours(1),
                base.plusHours(2));
        String token = login(student);

        mockMvc.perform(get("/api/v1/notifications?status=UNREAD")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].message").value("Sin leer"));

        mockMvc.perform(get("/api/v1/notifications?status=READ")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].message").value("Leida"))
                .andExpect(jsonPath("$.content[0].read").value(true));

        mockMvc.perform(get("/api/v1/notifications?status=ALL")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/notifications?status=BOGUS")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-06 - el mismo usuario ve solo las notificaciones del rol activo y el conteo del otro rol")
    void unreadCountAndListing_separateNotificationsByActiveRole() throws Exception {
        User dualRoleUser = saveUser("Dual Role", Role.STUDENT, AccountStatus.ACTIVE);
        tutorProfileRepository.save(TutorProfile.builder().user(dualRoleUser).career(career).verified(true).build());
        LocalDateTime base = LocalDateTime.of(2026, 10, 20, 10, 0);
        seedNotification(dualRoleUser, Role.STUDENT, NotificationType.BOOKING_CONFIRMED, "Como alumno", base, null);
        seedNotification(dualRoleUser, Role.TUTOR, NotificationType.BOOKING_CONFIRMED, "Como tutor 1",
                base.plusHours(1), null);
        seedNotification(dualRoleUser, Role.TUTOR, NotificationType.SESSION_COMPLETED, "Como tutor 2",
                base.plusHours(2), null);

        String studentToken = login(dualRoleUser);
        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].message").value("Como alumno"));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.otherRoleCount").value(2));

        dualRoleUser.setRole(Role.TUTOR);
        userRepository.saveAndFlush(dualRoleUser);
        String tutorToken = login(dualRoleUser);
        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].message").value("Como tutor 2"))
                .andExpect(jsonPath("$.content[1].message").value("Como tutor 1"));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.otherRoleCount").value(1));

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.otherRoleCount").value(1));

        dualRoleUser.setRole(Role.STUDENT);
        userRepository.saveAndFlush(dualRoleUser);
        String studentTokenAgain = login(dualRoleUser);
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentTokenAgain)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.otherRoleCount").value(0));
    }

    @Test
    @DisplayName("CP-07 - RATING_ENABLED informa actionStatus y las vencidas no cuentan como no leídas (CA 3, 4 y 7)")
    void ratingEnabled_exposesActionStatusAndExcludesExpiredFromUnread() throws Exception {
        User student = saveUser("Student Rating", Role.STUDENT, AccountStatus.ACTIVE);
        LocalDateTime base = LocalDateTime.now();
        Notification expired = seedRatingNotification(student, base.minusMinutes(8), base.minusMinutes(1), null);
        Notification completed = seedRatingNotification(student, base.minusMinutes(9), base.plusHours(24),
                base.minusMinutes(1));
        Notification pending = seedRatingNotification(student, base.minusMinutes(10), base.plusHours(24), null);
        String token = login(student);

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].notificationId").value(expired.getNotificationId().toString()))
                .andExpect(jsonPath("$.content[0].actionStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.content[0].expiresAt").exists())
                .andExpect(jsonPath("$.content[1].notificationId").value(completed.getNotificationId().toString()))
                .andExpect(jsonPath("$.content[1].actionStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.content[2].notificationId").value(pending.getNotificationId().toString()))
                .andExpect(jsonPath("$.content[2].actionStatus").value("PENDING"))
                .andExpect(jsonPath("$.content[2].expiresAt").exists());

        mockMvc.perform(get("/api/v1/notifications?status=UNREAD")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].notificationId").value(pending.getNotificationId().toString()));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    private Notification seedRatingNotification(User recipient, LocalDateTime createdAt, LocalDateTime expiresAt,
            LocalDateTime resolvedAt) {
        return notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .recipientRole(recipient.getRole())
                .type(NotificationType.RATING_ENABLED)
                .title("Calificá tu sesión")
                .message("Contanos cómo fue tu experiencia.")
                .resourceId(UUID.randomUUID())
                .dedupeKey(UUID.randomUUID().toString())
                .expiresAt(expiresAt)
                .resolvedAt(resolvedAt)
                .createdAt(createdAt)
                .readAt(resolvedAt)
                .build());
    }

    private Notification seedNotification(User recipient, NotificationType type, String message,
            LocalDateTime createdAt, LocalDateTime readAt) {
        return seedNotification(recipient, recipient.getRole(), type, message, createdAt, readAt);
    }

    private Notification seedNotification(User recipient, Role recipientRole, NotificationType type, String message,
            LocalDateTime createdAt, LocalDateTime readAt) {
        return notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .recipientRole(recipientRole)
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

    private CreatedBooking createBookingThroughService(User student, User tutor) {
        TutorProfile tutorProfile = tutorProfileRepository.findByUserId(tutor.getUserId()).orElseThrow();
        Subject subject = subjectRepository.save(Subject.builder()
                .name("Subject " + UUID.randomUUID()).isBasic(true).career(career).build());
        TutorSubject tutorSubject = tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .pricePerHour(new BigDecimal("2000.00"))
                .compensationType(CompensationType.FREE)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.BOTH)
                .build());
        LocalDate sessionDate = LocalDate.now(AppTimeZone.ZONE).plusDays(1);
        LocalTime startTime = LocalTime.of(15, 0);
        LocalTime endTime = LocalTime.of(16, 0);
        TimeSlot slot = timeSlotRepository.save(TimeSlot.builder()
                .date(sessionDate)
                .startTime(startTime)
                .endTime(endTime)
                .status(SlotStatus.AVAILABLE)
                .tutorProfileId(tutorProfile.getTutorProfileId())
                .build());
        holdRepository.save(Hold.builder()
                .timeSlot(slot)
                .student(student)
                .startTime(startTime)
                .endTime(endTime)
                .expiresAt(LocalDateTime.now(AppTimeZone.ZONE).plusMinutes(10))
                .build());

        UUID bookingId = bookingService.createBooking(student.getUserId(), new CreateBookingRequest(
                slot.getTimeSlotId() + "__booking",
                tutorSubject.getTutorSubjectId(),
                "Notification topic",
                Modality.VIRTUAL,
                sessionDate.atTime(startTime).atZone(AppTimeZone.ZONE).toInstant(),
                sessionDate.atTime(endTime).atZone(AppTimeZone.ZONE).toInstant())).bookingId();

        return new CreatedBooking(bookingId, subject.getName(), sessionDate);
    }

    private void awaitBookingConfirmedNotifications(UUID bookingId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (notificationRepository.countByResourceIdAndType(bookingId,
                    NotificationType.BOOKING_CONFIRMED) == 2) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for BOOKING_CONFIRMED notifications of booking " + bookingId);
    }

    private record CreatedBooking(UUID bookingId, String subjectName, LocalDate sessionDate) {
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