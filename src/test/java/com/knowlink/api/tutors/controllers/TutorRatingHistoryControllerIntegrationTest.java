package com.knowlink.api.tutors.controllers;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TutorRatingHistoryControllerIntegrationTest {

    private static final String BASE_PATH = "/api/v1/tutors/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IUserRepository userRepository;

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
    private IRatingRepository ratingRepository;

    private User tutor;
    private User student;
    private TutorProfile tutorProfile;
    private Subject subjectA;
    private Subject subjectB;
    private TutorSubject tutorSubjectA;
    private TutorSubject tutorSubjectB;
    private Booking bookingA1;
    private Booking bookingA2;
    private Booking bookingB1;

    @BeforeEach
    void setUp() {
        Career career = careerRepository.save(Career.builder()
                .name("Career " + UUID.randomUUID())
                .build());

        subjectA = subjectRepository.save(Subject.builder()
                .name("A Analisis Matematico I " + UUID.randomUUID())
                .isBasic(false)
                .career(career)
                .build());
        subjectB = subjectRepository.save(Subject.builder()
                .name("B Fisica I " + UUID.randomUUID())
                .isBasic(false)
                .career(career)
                .build());

        tutor = saveUser("Tutor Test", Role.TUTOR);
        student = saveUser("Student Test", Role.STUDENT);
        tutorProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(tutor)
                .career(career)
                .verified(true)
                .build());

        tutorSubjectA = saveTutorSubject(subjectA);
        tutorSubjectB = saveTutorSubject(subjectB);

        bookingA1 = createBooking(tutorSubjectA, BookingStatus.COMPLETED,
                LocalDate.of(2026, 9, 18), LocalTime.of(10, 0));
        bookingA2 = createBooking(tutorSubjectA, BookingStatus.COMPLETED,
                LocalDate.of(2026, 9, 24), LocalTime.of(10, 0));
        bookingB1 = createBooking(tutorSubjectB, BookingStatus.COMPLETED,
                LocalDate.of(2026, 9, 8), LocalTime.of(10, 0));

        createRating(bookingA1, 5, "Explica muy claro.", LocalDateTime.of(2026, 9, 20, 19, 30));
        createRating(bookingA2, 4, "Buen material de estudio.", LocalDateTime.of(2026, 9, 25, 12, 0));
        createRating(bookingB1, 3, "Podria ser mas rapido.", LocalDateTime.of(2026, 9, 10, 9, 0));
    }

    @Test
    @DisplayName("CP1/CA1: rating averages are broken down by subject")
    void cp1AveragesBrokenDownBySubject() throws Exception {
        mockMvc.perform(get(BASE_PATH + tutor.getUserId() + "/ratings").with(studentAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tutorId").value(tutor.getUserId().toString()))
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.totalRatings").value(3))
                .andExpect(jsonPath("$.subjects.length()").value(2))
                .andExpect(jsonPath("$.subjects[0].subjectId").value(subjectA.getSubjectId().toString()))
                .andExpect(jsonPath("$.subjects[0].name").value(subjectA.getName()))
                .andExpect(jsonPath("$.subjects[0].average").value(4.5))
                .andExpect(jsonPath("$.subjects[0].count").value(2))
                .andExpect(jsonPath("$.subjects[1].subjectId").value(subjectB.getSubjectId().toString()))
                .andExpect(jsonPath("$.subjects[1].average").value(3.0))
                .andExpect(jsonPath("$.subjects[1].count").value(1));
    }

    @Test
    @DisplayName("CP2/CA2: every comment carries the subjectId and subjectName of its session")
    void cp2CommentsIncludeTheirSubject() throws Exception {
        mockMvc.perform(get(BASE_PATH + tutor.getUserId() + "/ratings").with(studentAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.content.length()").value(3))
                .andExpect(jsonPath("$.comments.content[0].subjectId")
                        .value(subjectA.getSubjectId().toString()))
                .andExpect(jsonPath("$.comments.content[0].subjectName").value(subjectA.getName()))
                .andExpect(jsonPath("$.comments.content[2].subjectId")
                        .value(subjectB.getSubjectId().toString()))
                .andExpect(jsonPath("$.comments.content[2].subjectName").value(subjectB.getName()));
    }

    @Test
    @DisplayName("CP3/CA3: comments are sorted from newest to oldest")
    void cp3CommentsSortedDescending() throws Exception {
        mockMvc.perform(get(BASE_PATH + tutor.getUserId() + "/ratings").with(studentAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.content[0].ratingDate").value(containsDate("2026-09-25")))
                .andExpect(jsonPath("$.comments.content[1].ratingDate").value(containsDate("2026-09-20")))
                .andExpect(jsonPath("$.comments.content[2].ratingDate").value(containsDate("2026-09-10")));

        mockMvc.perform(get(BASE_PATH + tutor.getUserId() + "/ratings")
                        .param("size", "2")
                        .with(studentAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.content.length()").value(2))
                .andExpect(jsonPath("$.comments.page").value(0))
                .andExpect(jsonPath("$.comments.size").value(2))
                .andExpect(jsonPath("$.comments.totalElements").value(3))
                .andExpect(jsonPath("$.comments.totalPages").value(2));
    }

    @Test
    @DisplayName("CP4/CA4: tutor without rated sessions returns 200 with empty state")
    void cp4TutorWithoutRatings() throws Exception {
        User tutorWithoutRatings = saveUser("Tutor Sin Ratings", Role.TUTOR);
        Career career = careerRepository.save(Career.builder()
                .name("Career " + UUID.randomUUID())
                .build());
        tutorProfileRepository.save(TutorProfile.builder()
                .user(tutorWithoutRatings)
                .career(career)
                .build());

        mockMvc.perform(get(BASE_PATH + tutorWithoutRatings.getUserId() + "/ratings")
                        .with(studentAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").doesNotExist())
                .andExpect(jsonPath("$.totalRatings").value(0))
                .andExpect(jsonPath("$.subjects.length()").value(0))
                .andExpect(jsonPath("$.comments.content.length()").value(0))
                .andExpect(jsonPath("$.comments.totalElements").value(0));
    }

    @Test
    @DisplayName("CP5: unknown tutorId returns 404")
    void cp5UnknownTutorReturns404() throws Exception {
        mockMvc.perform(get(BASE_PATH + UUID.randomUUID() + "/ratings").with(studentAuth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("TUTOR_PROFILE_NOT_FOUND"));
    }

    @Test
    @DisplayName("CP6: subjectId filters comments without affecting subject averages")
    void cp6SubjectFilter() throws Exception {
        mockMvc.perform(get(BASE_PATH + tutor.getUserId() + "/ratings")
                        .param("subjectId", subjectB.getSubjectId().toString())
                        .with(studentAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.content.length()").value(1))
                .andExpect(jsonPath("$.comments.content[0].subjectId")
                        .value(subjectB.getSubjectId().toString()))
                .andExpect(jsonPath("$.subjects.length()").value(2))
                .andExpect(jsonPath("$.subjects[0].count").value(2))
                .andExpect(jsonPath("$.subjects[1].count").value(1));

        mockMvc.perform(get(BASE_PATH + tutor.getUserId() + "/ratings")
                        .param("subjectId", UUID.randomUUID().toString())
                        .with(studentAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.content.length()").value(0))
                .andExpect(jsonPath("$.subjects.length()").value(2));
    }

    @Test
    @DisplayName("CP7: a rating without comment counts in the average but not in the comments")
    void cp7RatingWithoutComment() throws Exception {
        Booking booking = createBooking(tutorSubjectB, BookingStatus.COMPLETED,
                LocalDate.of(2026, 9, 26), LocalTime.of(15, 0));
        createRating(booking, 1, null, LocalDateTime.of(2026, 9, 27, 10, 0));

        mockMvc.perform(get(BASE_PATH + tutor.getUserId() + "/ratings").with(studentAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRatings").value(4))
                .andExpect(jsonPath("$.averageRating").value(3.3))
                .andExpect(jsonPath("$.subjects[1].average").value(2.0))
                .andExpect(jsonPath("$.subjects[1].count").value(2))
                .andExpect(jsonPath("$.comments.content.length()").value(3))
                .andExpect(jsonPath("$.comments.totalElements").value(3));
    }

    @Test
    @DisplayName("Only visible ratings from completed sessions are counted")
    void onlyVisibleRatingsFromCompletedSessions() throws Exception {
        Booking cancelled = createBooking(tutorSubjectA, BookingStatus.CANCELLED,
                LocalDate.of(2026, 9, 28), LocalTime.of(18, 0));
        ratingRepository.save(Rating.builder()
                .booking(cancelled)
                .ratedUser(tutor)
                .raterUser(student)
                .score(1)
                .comment("Sesion cancelada.")
                .ratingDate(LocalDateTime.of(2026, 9, 29, 10, 0))
                .visible(true)
                .build());
        Booking hiddenSession = createBooking(tutorSubjectA, BookingStatus.COMPLETED,
                LocalDate.of(2026, 9, 27), LocalTime.of(11, 0));
        ratingRepository.save(Rating.builder()
                .booking(hiddenSession)
                .ratedUser(tutor)
                .raterUser(student)
                .score(1)
                .comment("Comentario oculto.")
                .ratingDate(LocalDateTime.of(2026, 9, 30, 10, 0))
                .visible(false)
                .build());

        mockMvc.perform(get(BASE_PATH + tutor.getUserId() + "/ratings").with(studentAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRatings").value(3))
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.comments.content.length()").value(3));
    }

    @Test
    @DisplayName("Page size is capped at 50")
    void pageSizeIsCappedAt50() throws Exception {
        mockMvc.perform(get(BASE_PATH + tutor.getUserId() + "/ratings")
                        .param("size", "999")
                        .with(studentAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.size").value(50));
    }

    @Test
    @DisplayName("Unauthenticated request returns 401")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get(BASE_PATH + tutor.getUserId() + "/ratings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A TUTOR role cannot read the rating history (403)")
    void tutorRoleReturns403() throws Exception {
        mockMvc.perform(get(BASE_PATH + tutor.getUserId() + "/ratings").with(tutorAuth()))
                .andExpect(status().isForbidden());
    }

    private Matcher<String> containsDate(String datePrefix) {
        return containsString(datePrefix);
    }

    private User saveUser(String fullName, Role role) {
        return userRepository.save(User.builder()
                .fullName(fullName)
                .email(fullName.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID() + "@test.com")
                .password("password123")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private TutorSubject saveTutorSubject(Subject subject) {
        return tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .pricePerHour(new BigDecimal("2000.00"))
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build());
    }

    private Booking createBooking(TutorSubject tutorSubject, BookingStatus status, LocalDate date,
            LocalTime startTime) {
        LocalTime endTime = startTime.plusHours(1);
        TimeSlot slot = timeSlotRepository.save(TimeSlot.builder()
                .date(date)
                .startTime(startTime)
                .endTime(endTime)
                .status(SlotStatus.OCCUPIED)
                .tutorProfileId(tutorProfile.getTutorProfileId())
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
                .tutorSubject(tutorSubject)
                .student(student)
                .tutor(tutor)
                .build());
    }

    private Rating createRating(Booking booking, Integer score, String comment, LocalDateTime ratingDate) {
        return ratingRepository.save(Rating.builder()
                .booking(booking)
                .ratedUser(tutor)
                .raterUser(student)
                .score(score)
                .comment(comment)
                .ratingDate(ratingDate)
                .visible(true)
                .build());
    }

    private RequestPostProcessor studentAuth() {
        UserPrincipal principal = new UserPrincipal(student);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private RequestPostProcessor tutorAuth() {
        UserPrincipal principal = new UserPrincipal(tutor);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}