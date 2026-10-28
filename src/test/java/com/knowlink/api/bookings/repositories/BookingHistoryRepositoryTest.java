package com.knowlink.api.bookings.repositories;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class BookingHistoryRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final LocalTime NOW = LocalTime.of(12, 0);
    private static final List<BookingStatus> RESERVED_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.BOOKED);

    @Autowired
    private IBookingRepository bookingRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private User student;
    private User tutor;
    private TutorProfile tutorProfile;
    private TutorSubject tutorSubject;

    @BeforeEach
    void setUp() {
        Career career = persist(Career.builder().name("Test Career").build());
        student = persistUser("student", Role.STUDENT);
        tutor = persistUser("tutor", Role.TUTOR);
        tutorProfile = persist(TutorProfile.builder().user(tutor).career(career).build());
        Subject subject = persist(Subject.builder()
                .name("Test Subject")
                .career(career)
                .isBasic(true)
                .build());
        tutorSubject = persist(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .modality(Modality.VIRTUAL)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("RESERVED incluye futuras y las de hoy que todavía no terminaron")
    void upcomingOnlyKeepsFutureAndRunningTodayBookings() {
        Booking yesterday = persistBooking(TODAY.minusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0),
                BookingStatus.BOOKED);
        Booking todayFinished = persistBooking(TODAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                BookingStatus.BOOKED);
        Booking todayRunning = persistBooking(TODAY, LocalTime.of(11, 30), LocalTime.of(12, 30),
                BookingStatus.BOOKED);
        Booking tomorrow = persistBooking(TODAY.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0),
                BookingStatus.BOOKED);
        entityManager.flush();

        Page<Booking> upcoming = bookingRepository.findHistoryByStudentAsc(
                student.getUserId(), RESERVED_STATUSES, false, false, true, TODAY, NOW,
                PageRequest.of(0, 10));

        assertThat(upcoming.getContent())
                .extracting(Booking::getBookingId)
                .containsExactly(todayRunning.getBookingId(), tomorrow.getBookingId())
                .doesNotContain(yesterday.getBookingId(), todayFinished.getBookingId());
    }

    @Test
    @DisplayName("Sin filtro upcomingOnly el historial conserva las reservas pasadas")
    void withoutUpcomingFilterKeepsPastBookings() {
        Booking yesterday = persistBooking(TODAY.minusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0),
                BookingStatus.BOOKED);
        Booking tomorrow = persistBooking(TODAY.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0),
                BookingStatus.BOOKED);
        entityManager.flush();

        Page<Booking> history = bookingRepository.findHistoryByStudentDesc(
                student.getUserId(), RESERVED_STATUSES, false, false, false, TODAY, NOW,
                PageRequest.of(0, 10));

        assertThat(history.getContent())
                .extracting(Booking::getBookingId)
                .containsExactly(tomorrow.getBookingId(), yesterday.getBookingId());
    }

    @Test
    @DisplayName("NOT_CONFIRMED se consulta como categoría propia")
    void notConfirmedHasItsOwnStatusFilter() {
        Booking notConfirmed = persistBooking(TODAY.minusDays(2), LocalTime.of(10, 0), LocalTime.of(11, 0),
                BookingStatus.NOT_CONFIRMED);
        persistBooking(TODAY.minusDays(2), LocalTime.of(12, 0), LocalTime.of(13, 0),
                BookingStatus.CANCELLED);
        entityManager.flush();

        Page<Booking> history = bookingRepository.findHistoryByStudentDesc(
                student.getUserId(), List.of(BookingStatus.NOT_CONFIRMED), false, false, false, TODAY, NOW,
                PageRequest.of(0, 10));

        assertThat(history.getContent())
                .extracting(Booking::getBookingId)
                .containsExactly(notConfirmed.getBookingId());
    }

    @Test
    @DisplayName("El filtro de tutor aplica la misma regla de reservas vigentes")
    void tutorUpcomingOnlyAlsoFiltersByDate() {
        Booking yesterday = persistBooking(TODAY.minusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0),
                BookingStatus.BOOKED);
        Booking tomorrow = persistBooking(TODAY.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0),
                BookingStatus.BOOKED);
        entityManager.flush();

        Page<Booking> upcoming = bookingRepository.findHistoryByTutorAsc(
                tutor.getUserId(), RESERVED_STATUSES, false, false, true, TODAY, NOW,
                PageRequest.of(0, 10));

        assertThat(upcoming.getContent())
                .extracting(Booking::getBookingId)
                .containsExactly(tomorrow.getBookingId())
                .doesNotContain(yesterday.getBookingId());
    }

    private Booking persistBooking(LocalDate date, LocalTime start, LocalTime end, BookingStatus status) {
        TimeSlot timeSlot = persist(TimeSlot.builder()
                .date(date)
                .startTime(start)
                .endTime(end)
                .status(SlotStatus.OCCUPIED)
                .tutorProfileId(tutorProfile.getTutorProfileId())
                .build());
        return persist(Booking.builder()
                .amount(BigDecimal.TEN)
                .sessionDate(date)
                .startTime(start)
                .endTime(end)
                .modality(Modality.VIRTUAL)
                .bookingStatus(status)
                .timeSlot(timeSlot)
                .tutorSubject(tutorSubject)
                .student(student)
                .tutor(tutor)
                .build());
    }

    private User persistUser(String localName, Role role) {
        return persist(User.builder()
                .fullName(localName)
                .email(localName + "@history.test")
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
