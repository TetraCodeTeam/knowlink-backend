package com.knowlink.api.ratings.services;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.ratings.controllers.responses.PendingRatingItemResponse;
import com.knowlink.api.ratings.controllers.responses.PendingRatingsResponse;
import com.knowlink.api.ratings.gate.RatingGateProperties;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.ratings.services.implementations.RatingGateServiceImpl;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingGateServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Cordoba");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 20, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    private static final LocalDateTime ENFORCED_FROM = LocalDateTime.of(2026, 11, 1, 0, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final LocalTime NOW_TIME = NOW.toLocalTime();
    private static final LocalDate FROM_DATE = ENFORCED_FROM.toLocalDate();
    private static final LocalTime FROM_TIME = ENFORCED_FROM.toLocalTime();

    @Mock
    private IRatingRepository ratingRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("bloqueo apagado: no consulta la base y no bloquea")
    void disabled_doesNotQueryDatabase() {
        RatingGateServiceImpl service = serviceWithEnforcedFrom("");

        assertThat(service.isBlocked(userId)).isFalse();

        PendingRatingsResponse pending = service.getPending(userId);
        assertThat(pending.blocking()).isEmpty();
        assertThat(pending.nextBlockAt()).isNull();
        verifyNoInteractions(ratingRepository);
    }

    @Test
    @DisplayName("usuario con sesión COMPLETED terminada y sin calificar queda bloqueado")
    void blockingBooking_blocksUser() {
        RatingGateServiceImpl service = serviceWithEnforcedFrom(ENFORCED_FROM.toString());
        when(ratingRepository.existsBlockingBooking(userId, BookingStatus.COMPLETED, TODAY, NOW_TIME,
                FROM_DATE, FROM_TIME)).thenReturn(true);

        assertThat(service.isBlocked(userId)).isTrue();
    }

    @Test
    @DisplayName("resultado no bloqueado se cachea y evita la segunda consulta")
    void notBlocked_isNegativelyCached() {
        RatingGateServiceImpl service = serviceWithEnforcedFrom(ENFORCED_FROM.toString());
        when(ratingRepository.existsBlockingBooking(userId, BookingStatus.COMPLETED, TODAY, NOW_TIME,
                FROM_DATE, FROM_TIME)).thenReturn(false);

        assertThat(service.isBlocked(userId)).isFalse();
        assertThat(service.isBlocked(userId)).isFalse();

        verify(ratingRepository, times(1)).existsBlockingBooking(userId, BookingStatus.COMPLETED, TODAY,
                NOW_TIME, FROM_DATE, FROM_TIME);
    }

    @Test
    @DisplayName("resultado bloqueado nunca se cachea")
    void blocked_isNeverCached() {
        RatingGateServiceImpl service = serviceWithEnforcedFrom(ENFORCED_FROM.toString());
        when(ratingRepository.existsBlockingBooking(userId, BookingStatus.COMPLETED, TODAY, NOW_TIME,
                FROM_DATE, FROM_TIME)).thenReturn(true);

        assertThat(service.isBlocked(userId)).isTrue();
        assertThat(service.isBlocked(userId)).isTrue();

        verify(ratingRepository, times(2)).existsBlockingBooking(userId, BookingStatus.COMPLETED, TODAY,
                NOW_TIME, FROM_DATE, FROM_TIME);
    }

    @Test
    @DisplayName("getPending arma rol, materia, otra parte y orden de las sesiones")
    void getPending_mapsItemsInOrderAndRoles() {
        RatingGateServiceImpl service = serviceWithEnforcedFrom(ENFORCED_FROM.toString());
        User studentAsRater = userWithId(userId, "Alumno Uno");
        User tutor = user("Tutor Uno");
        User otherStudent = user("Alumno Dos");
        User tutorAsRater = userWithId(userId, "Tutor Dos");

        Booking asStudent = booking(studentAsRater, tutor, "Análisis Matemático I",
                LocalDate.of(2026, 11, 6), LocalTime.of(10, 0), LocalTime.of(11, 0));
        Booking asTutor = booking(otherStudent, tutorAsRater, "Física I",
                LocalDate.of(2026, 11, 7), LocalTime.of(12, 0), LocalTime.of(13, 0));

        when(ratingRepository.findBlockingBookings(userId, BookingStatus.COMPLETED, TODAY, NOW_TIME,
                FROM_DATE, FROM_TIME)).thenReturn(List.of(asStudent, asTutor));
        when(ratingRepository.findUpcomingUnratedCompleted(eq(userId), eq(BookingStatus.COMPLETED), eq(TODAY),
                eq(NOW_TIME), eq(FROM_DATE), eq(FROM_TIME), any(Pageable.class))).thenReturn(List.of());

        PendingRatingsResponse pending = service.getPending(userId);

        assertThat(pending.blocking()).hasSize(2);
        PendingRatingItemResponse first = pending.blocking().get(0);
        assertThat(first.bookingId()).isEqualTo(asStudent.getBookingId());
        assertThat(first.raterRole()).isEqualTo(Role.STUDENT);
        assertThat(first.subjectName()).isEqualTo("Análisis Matemático I");
        assertThat(first.otherPartyFullName()).isEqualTo("Tutor Uno");

        // The second booking is the tutor side even though it is the same tutor user.
        PendingRatingItemResponse second = pending.blocking().get(1);
        assertThat(second.bookingId()).isEqualTo(asTutor.getBookingId());
        assertThat(second.raterRole()).isEqualTo(Role.TUTOR);
        assertThat(second.subjectName()).isEqualTo("Física I");
        assertThat(second.otherPartyFullName()).isEqualTo("Alumno Dos");
    }

    @Test
    @DisplayName("getPending calcula nextBlockAt con el fin más próximo de una sesión no calificada")
    void getPending_computesNextBlockAt() {
        RatingGateServiceImpl service = serviceWithEnforcedFrom(ENFORCED_FROM.toString());
        Booking upcoming = booking(user("Alumno"), user("Tutor"), "Química",
                LocalDate.of(2026, 11, 8), LocalTime.of(14, 0), LocalTime.of(15, 30));

        when(ratingRepository.findBlockingBookings(userId, BookingStatus.COMPLETED, TODAY, NOW_TIME,
                FROM_DATE, FROM_TIME)).thenReturn(List.of());
        when(ratingRepository.findUpcomingUnratedCompleted(eq(userId), eq(BookingStatus.COMPLETED), eq(TODAY),
                eq(NOW_TIME), eq(FROM_DATE), eq(FROM_TIME), any(Pageable.class))).thenReturn(List.of(upcoming));

        PendingRatingsResponse pending = service.getPending(userId);

        assertThat(pending.blocking()).isEmpty();
        assertThat(pending.nextBlockAt()).isEqualTo(LocalDateTime.of(2026, 11, 8, 15, 30));
    }

    private RatingGateServiceImpl serviceWithEnforcedFrom(String enforcedFrom) {
        return new RatingGateServiceImpl(ratingRepository,
                new RatingGateProperties(enforcedFrom, 15), CLOCK);
    }

    private User user(String fullName) {
        return userWithId(UUID.randomUUID(), fullName);
    }

    private User userWithId(UUID id, String fullName) {
        return User.builder().userId(id).fullName(fullName).build();
    }

    private Booking booking(User student, User tutor, String subjectName, LocalDate sessionDate,
            LocalTime startTime, LocalTime endTime) {
        TutorSubject tutorSubject = TutorSubject.builder()
                .subject(Subject.builder().name(subjectName).build())
                .build();
        return Booking.builder()
                .bookingId(UUID.randomUUID())
                .student(student)
                .tutor(tutor)
                .tutorSubject(tutorSubject)
                .bookingStatus(BookingStatus.COMPLETED)
                .sessionDate(sessionDate)
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }
}
