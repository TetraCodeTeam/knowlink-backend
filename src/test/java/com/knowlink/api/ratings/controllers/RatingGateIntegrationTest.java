package com.knowlink.api.ratings.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.testsupport.CatalogFixtures;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rating-gate.enforced-from=2020-01-01T00:00:00",
        "rating-gate.clear-cache-seconds=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RatingGateIntegrationTest {

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
    private IStudentProfileRepository studentProfileRepository;

    @Autowired
    private ICareerRepository careerRepository;

    @Autowired
    private IInstitutionRepository institutionRepository;

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

    @Test
    @DisplayName("Alumno bloqueado: 423, pending con sus datos, califica y se desbloquea")
    void blockedStudent_canSeePendingAndUnblockByRating() throws Exception {
        Fixture fixture = createFixture();
        String token = login(fixture.student());

        mockMvc.perform(get("/api/v1/bookings/mine")
                        .param("role", Role.STUDENT.name())
                        .param("category", "RESERVED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.status").value(423))
                .andExpect(jsonPath("$.detail").value("RATING_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("Tenés que calificar tu sesión para poder seguir usando KnowLink."));

        mockMvc.perform(get("/api/v1/ratings/pending")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocking[0].bookingId").value(fixture.booking().getBookingId().toString()))
                .andExpect(jsonPath("$.blocking[0].raterRole").value("STUDENT"))
                .andExpect(jsonPath("$.blocking[0].subjectName").value(fixture.subjectName()))
                .andExpect(jsonPath("$.blocking[0].otherPartyFullName").value(fixture.tutor().getFullName()))
                .andExpect(jsonPath("$.nextBlockAt").value(nullValue()));

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/ratings", fixture.booking().getBookingId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":4,\"comment\":\"Muy buena clase\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/bookings/mine")
                        .param("role", Role.STUDENT.name())
                        .param("category", "RESERVED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bookings/{bookingId}", fixture.booking().getBookingId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.ratingDeadline").value(nullValue()));
    }

    @Test
    @DisplayName("Tutor bloqueado: 423 y pending informa raterRole TUTOR")
    void blockedTutor_gets423AndTutorRole() throws Exception {
        Fixture fixture = createFixture();
        String token = login(fixture.tutor());

        mockMvc.perform(get("/api/v1/bookings/mine")
                        .param("role", Role.TUTOR.name())
                        .param("category", "RESERVED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.detail").value("RATING_REQUIRED"));

        mockMvc.perform(get("/api/v1/ratings/pending")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocking[0].raterRole").value("TUTOR"))
                .andExpect(jsonPath("$.blocking[0].otherPartyFullName").value(fixture.student().getFullName()));
    }

    @Test
    @DisplayName("Estando bloqueado, pending, logout y OPTIONS no responden 423")
    void exemptEndpoints_doNotReturn423() throws Exception {
        Fixture fixture = createFixture();
        String token = login(fixture.student());

        mockMvc.perform(get("/api/v1/ratings/pending")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(options("/api/v1/bookings/mine")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/ratings", fixture.booking().getBookingId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":9}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("ADMIN nunca responde 423")
    void admin_isNeverBlocked() throws Exception {
        User admin = saveUser("Admin Gate", Role.ADMIN, null);
        String token = login(admin);

        mockMvc.perform(get("/api/v1/users/{userId}", admin.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private Fixture createFixture() {
        Institution institution = CatalogFixtures.saveInstitution(institutionRepository, "Test " + System.nanoTime());
        Career career = careerRepository.save(CatalogFixtures.career(institution, "Career " + UUID.randomUUID()));
        User student = saveUser("Student " + UUID.randomUUID(), Role.STUDENT, career);
        User tutor = saveUser("Tutor " + UUID.randomUUID(), Role.TUTOR, career);
        TutorProfile tutorProfile = tutorProfileRepository.findByUserId(tutor.getUserId()).orElseThrow();
        Subject subject = subjectRepository.save(CatalogFixtures.subject(career, "Subject " + UUID.randomUUID()));
        TutorSubject tutorSubject = tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .pricePerHour(new BigDecimal("2000.00"))
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build());

        LocalDateTime sessionEnd = LocalDateTime.now(AppTimeZone.ZONE).minusHours(1);
        LocalTime endTime = sessionEnd.toLocalTime();
        LocalTime startTime = endTime.minusHours(1);
        TimeSlot slot = timeSlotRepository.save(TimeSlot.builder()
                .date(sessionEnd.toLocalDate())
                .startTime(startTime)
                .endTime(endTime)
                .status(SlotStatus.OCCUPIED)
                .tutorProfileId(tutorProfile.getTutorProfileId())
                .build());

        Booking booking = bookingRepository.save(Booking.builder()
                .amount(new BigDecimal("2000.00"))
                .sessionDate(sessionEnd.toLocalDate())
                .startTime(startTime)
                .endTime(endTime)
                .modality(Modality.VIRTUAL)
                .bookingStatus(BookingStatus.COMPLETED)
                .timeSlot(slot)
                .tutorSubject(tutorSubject)
                .student(student)
                .tutor(tutor)
                .confirmedAt(sessionEnd)
                .build());

        return new Fixture(student, tutor, booking, subject.getName());
    }

    private User saveUser(String fullName, Role role, Career career) {
        User user = userRepository.save(User.builder()
                .fullName(fullName)
                .email(UUID.randomUUID() + "@test.com")
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        if (role == Role.STUDENT) {
            studentProfileRepository.save(StudentProfile.builder().user(user).career(career).build());
        }
        if (role == Role.TUTOR) {
            tutorProfileRepository.save(TutorProfile.builder()
                    .user(user)
                    .career(career)
                    .verified(true)
                    .build());
        }
        return user;
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

    private record Fixture(User student, User tutor, Booking booking, String subjectName) {
    }
}
