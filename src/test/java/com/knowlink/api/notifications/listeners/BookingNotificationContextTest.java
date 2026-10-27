package com.knowlink.api.notifications.listeners;

import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingNotificationContextTest {

    @Mock
    private IBookingRepository bookingRepository;

    @InjectMocks
    private BookingNotificationContext context;

    private final UUID bookingId = UUID.randomUUID();

    @Test
    @DisplayName("Snapshot - usa el nombre de la materia cuando existe")
    void loadSnapshot_resolvesSubjectName() {
        Booking booking = booking(withSubject("Sistemas Operativos"));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        Optional<BookingSnapshot> snapshot = context.loadSnapshot(bookingId);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().subjectName()).isEqualTo("Sistemas Operativos");
        assertThat(snapshot.get().studentUserId()).isEqualTo(booking.getStudent().getUserId());
        assertThat(snapshot.get().tutorUserId()).isEqualTo(booking.getTutor().getUserId());
        assertThat(snapshot.get().endTime()).isEqualTo(booking.getEndTime());
        assertThat(snapshot.get().confirmedAt()).isEqualTo(booking.getConfirmedAt());
    }

    @Test
    @DisplayName("Snapshot sin tutorSubject - la materia cae a un nombre generico")
    void loadSnapshot_withoutTutorSubject_fallsBackToGenericSubject() {
        Booking booking = booking(null);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThat(context.loadSnapshot(bookingId)).hasValueSatisfying(
                snapshot -> assertThat(snapshot.subjectName()).isEqualTo("una materia"));
    }

    @Test
    @DisplayName("Snapshot con tutorSubject sin materia - la materia cae a un nombre generico")
    void loadSnapshot_tutorSubjectWithoutSubject_fallsBackToGenericSubject() {
        Booking booking = booking(TutorSubject.builder().build());
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThat(context.loadSnapshot(bookingId)).hasValueSatisfying(
                snapshot -> assertThat(snapshot.subjectName()).isEqualTo("una materia"));
    }

    @Test
    @DisplayName("Reserva inexistente - optional vacio")
    void loadSnapshot_missingBooking_returnsEmpty() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThat(context.loadSnapshot(bookingId)).isEmpty();
    }

    private TutorSubject withSubject(String subjectName) {
        return TutorSubject.builder().subject(Subject.builder().name(subjectName).build()).build();
    }

    private Booking booking(TutorSubject tutorSubject) {
        return Booking.builder()
                .bookingId(bookingId)
                .sessionDate(LocalDate.of(2026, 10, 20))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .confirmedAt(LocalDateTime.of(2026, 10, 20, 9, 5))
                .student(User.builder().userId(UUID.randomUUID()).fullName("Alumno Test").build())
                .tutor(User.builder().userId(UUID.randomUUID()).fullName("Tutor Test").build())
                .tutorSubject(tutorSubject)
                .build();
    }
}