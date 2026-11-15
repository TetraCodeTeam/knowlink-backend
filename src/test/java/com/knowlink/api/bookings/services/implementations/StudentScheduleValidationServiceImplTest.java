package com.knowlink.api.bookings.services.implementations;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.tutors.availability.data.enums.BookingStatusGroups;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentScheduleValidationServiceImplTest {

    @Mock
    private IBookingRepository bookingRepository;

    @InjectMocks
    private StudentScheduleValidationServiceImpl studentScheduleValidationService;

    private final UUID studentId = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 11, 20);
    private final User student = User.builder().userId(studentId).fullName("Lucía Pérez").build();

    @Test
    @DisplayName("CP-SCHED.01 - una reserva COMPLETED del mismo día sigue generando conflicto de horario")
    void rejectsOverlapWithCompletedBooking() {
        when(bookingRepository.findActiveByStudentAndDate(studentId, date, BookingStatusGroups.OCCUPYING))
                .thenReturn(List.of(buildBooking(LocalTime.of(14, 0), LocalTime.of(15, 0),
                        BookingStatus.COMPLETED)));

        assertThatThrownBy(() -> studentScheduleValidationService.validateNoTimeConflict(
                        student, date, LocalTime.of(14, 30), LocalTime.of(15, 30)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Ana García");
    }

    @Test
    @DisplayName("CP-SCHED.02 - una reserva COMPLETED no bloquea un horario del mismo día que no se superpone")
    void acceptsNonOverlappingWindowAfterCompletedBooking() {
        when(bookingRepository.findActiveByStudentAndDate(studentId, date, BookingStatusGroups.OCCUPYING))
                .thenReturn(List.of(buildBooking(LocalTime.of(14, 0), LocalTime.of(15, 0),
                        BookingStatus.COMPLETED)));

        assertThatCode(() -> studentScheduleValidationService.validateNoTimeConflict(
                        student, date, LocalTime.of(15, 0), LocalTime.of(16, 0)))
                .doesNotThrowAnyException();
    }

    private Booking buildBooking(LocalTime start, LocalTime end, BookingStatus status) {
        User tutor = User.builder().userId(UUID.randomUUID()).fullName("Ana García").build();
        return Booking.builder()
                .bookingId(UUID.randomUUID())
                .sessionDate(date)
                .startTime(start)
                .endTime(end)
                .bookingStatus(status)
                .tutor(tutor)
                .build();
    }
}