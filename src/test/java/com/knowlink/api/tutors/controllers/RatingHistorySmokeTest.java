package com.knowlink.api.tutors.controllers;
import com.knowlink.api.catalog.data.models.Institution;
import com.knowlink.api.catalog.repositories.IInstitutionRepository;

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
import com.knowlink.api.tutors.data.models.Rating;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.IRatingRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RatingHistorySmokeTest {

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
    private IInstitutionRepository institutionRepository;

    @Autowired
    private IRatingRepository ratingRepository;

    private User studentUser;

    private void print(String label, MvcResult result) throws Exception {
        System.out.println("===== " + label + " =====");
        System.out.println("HTTP " + result.getResponse().getStatus());
        System.out.println(result.getResponse().getContentAsString());
        System.out.println();
    }

    @Test
    @DisplayName("Smoke: consultar historial de calificaciones de un tutor")
    void queryRatingHistory() throws Exception {
        Institution institution = institutionRepository.save(Institution.builder()
                .name("Test Institution " + UUID.randomUUID())
                .build());
        Career career = careerRepository.save(Career.builder().name("Career " + UUID.randomUUID()).institution(institution).build());
        Subject subjectA = subjectRepository.save(Subject.builder()
                .name("A Analisis Matematico I " + UUID.randomUUID()).isBasic(false).institution(institution).careers(java.util.Set.of(career)).build());
        Subject subjectB = subjectRepository.save(Subject.builder()
                .name("B Fisica I " + UUID.randomUUID()).isBasic(false).institution(institution).careers(java.util.Set.of(career)).build());

        User tutor = saveUser("Juan Perez", Role.TUTOR);
        studentUser = saveUser("Maria Lopez", Role.STUDENT);
        TutorProfile profile = tutorProfileRepository.save(TutorProfile.builder()
                .user(tutor).career(career).verified(true).build());

        TutorSubject tsA = saveTutorSubject(profile, subjectA);
        TutorSubject tsB = saveTutorSubject(profile, subjectB);

        Booking bA1 = createBooking(profile, tsA, BookingStatus.COMPLETED, LocalDate.of(2026, 9, 18), LocalTime.of(10, 0));
        Booking bA2 = createBooking(profile, tsA, BookingStatus.COMPLETED, LocalDate.of(2026, 9, 24), LocalTime.of(10, 0));
        Booking bB1 = createBooking(profile, tsB, BookingStatus.COMPLETED, LocalDate.of(2026, 9, 8), LocalTime.of(10, 0));

        createRating(bA1, tutor, studentUser, 5, "Explica muy claro.", LocalDateTime.of(2026, 9, 20, 19, 30));
        createRating(bA2, tutor, studentUser, 4, "Buen material de estudio.", LocalDateTime.of(2026, 9, 25, 12, 0));
        createRating(bB1, tutor, studentUser, 3, null, LocalDateTime.of(2026, 9, 10, 9, 0));

        String base = "/api/v1/tutors/" + tutor.getUserId() + "/ratings";

        print("1) GET " + base,
                mockMvc.perform(get(base).with(studentAuth(studentUser)))
                        .andExpect(status().isOk()).andReturn());

        print("2) GET " + base + "?subjectId=" + subjectB.getSubjectId(),
                mockMvc.perform(get(base).param("subjectId", subjectB.getSubjectId().toString())
                        .with(studentAuth(studentUser)))
                        .andExpect(status().isOk()).andReturn());

        print("3) GET " + base + "?page=0&size=2 (paginacion)",
                mockMvc.perform(get(base).param("page", "0").param("size", "2")
                        .with(studentAuth(studentUser)))
                        .andExpect(status().isOk()).andReturn());

        print("4) GET /api/v1/tutors/" + UUID.randomUUID() + "/ratings (tutor inexistente -> 404)",
                mockMvc.perform(get("/api/v1/tutors/" + UUID.randomUUID() + "/ratings")
                        .with(studentAuth(studentUser)))
                        .andExpect(status().isNotFound()).andReturn());

        print("5) GET " + base + " sin token (-> 401)",
                mockMvc.perform(get(base)).andExpect(status().isUnauthorized()).andReturn());
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

    private TutorSubject saveTutorSubject(TutorProfile profile, Subject subject) {
        return tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(profile)
                .subject(subject)
                .pricePerHour(new BigDecimal("2000.00"))
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build());
    }

    private Booking createBooking(TutorProfile profile, TutorSubject tutorSubject, BookingStatus status,
            LocalDate date, LocalTime startTime) {
        LocalTime endTime = startTime.plusHours(1);
        TimeSlot slot = timeSlotRepository.save(TimeSlot.builder()
                .date(date).startTime(startTime).endTime(endTime)
                .status(SlotStatus.OCCUPIED)
                .tutorProfileId(profile.getTutorProfileId())
                .build());
        return bookingRepository.save(Booking.builder()
                .amount(new BigDecimal("2100.00"))
                .sessionDate(date).startTime(startTime).endTime(endTime)
                .topic("Sesion de prueba")
                .modality(Modality.VIRTUAL)
                .bookingStatus(status)
                .timeSlot(slot)
                .tutorSubject(tutorSubject)
                .student(studentUser)
                .tutor(profile.getUser())
                .build());
    }


    private void createRating(Booking booking, User tutor, User student, Integer score, String comment,
            LocalDateTime ratingDate) {
        ratingRepository.save(Rating.builder()
                .booking(booking)
                .ratedUser(tutor)
                .raterUser(student)
                .score(score)
                .comment(comment)
                .ratingDate(ratingDate)
                .visible(true)
                .build());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor studentAuth(User student) {
        UserPrincipal principal = new UserPrincipal(student);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}