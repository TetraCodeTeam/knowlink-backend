package com.knowlink.api.bookings.validations;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.services.interfaces.IStudentScheduleValidationService;
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
class BookingValidationServiceImplTest {

    @Mock
    private IBookingRepository bookingRepository;
    @Mock
    private IStudentScheduleValidationService studentScheduleValidationService;

    @InjectMocks
    private BookingValidationServiceImpl bookingValidationService;

    private final UUID timeSlotId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID tutorSubjectId = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 11, 20);
    private final User student = User.builder().userId(studentId).fullName("Lucía Pérez").build();

    @Test
    @DisplayName("CP-BKG.01 - una reserva COMPLETED sigue ocupando su horario al crear otra reserva")
    void validateNoOverlap_rejectsOverlappingCompletedBooking() {
        when(bookingRepository.findActiveByTimeSlotId(timeSlotId, BookingStatusGroups.OCCUPYING))
                .thenReturn(List.of(buildBooking(LocalTime.of(14, 0), LocalTime.of(15, 0),
                        BookingStatus.COMPLETED)));

        assertThatThrownBy(() -> bookingValidationService.validateNoOverlap(
                        timeSlotId, LocalTime.of(14, 30), LocalTime.of(15, 30)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ya fue reservado");
    }

    @Test
    @DisplayName("CP-BKG.02 - una reserva COMPLETED no bloquea un horario que no se superpone")
    void validateNoOverlap_acceptsNonOverlappingCompletedBooking() {
        when(bookingRepository.findActiveByTimeSlotId(timeSlotId, BookingStatusGroups.OCCUPYING))
                .thenReturn(List.of(buildBooking(LocalTime.of(14, 0), LocalTime.of(15, 0),
                        BookingStatus.COMPLETED)));

        assertThatCode(() -> bookingValidationService.validateNoOverlap(
                        timeSlotId, LocalTime.of(15, 0), LocalTime.of(16, 0)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CP-BKG.03 - las reservas COMPLETED del día cuentan para el cupo de 3 horas por materia")
    void validateDailySubjectCap_countsCompletedBookings() {
        when(bookingRepository.findActiveByStudentSubjectAndDate(studentId, tutorSubjectId, date,
                        BookingStatusGroups.OCCUPYING))
                .thenReturn(List.of(
                        buildBooking(LocalTime.of(14, 0), LocalTime.of(16, 0), BookingStatus.COMPLETED)));

        assertThatThrownBy(() -> bookingValidationService.validateDailySubjectCap(
                        student, tutorSubjectId, date, LocalTime.of(17, 0), LocalTime.of(19, 0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("3 horas diarias");
    }

    @Test
    @DisplayName("CP-BKG.04 - reservas COMPLETED dentro del cupo diario no bloquean")
    void validateDailySubjectCap_allowsCompletedBookingsWithinCap() {
        when(bookingRepository.findActiveByStudentSubjectAndDate(studentId, tutorSubjectId, date,
                        BookingStatusGroups.OCCUPYING))
                .thenReturn(List.of(
                        buildBooking(LocalTime.of(14, 0), LocalTime.of(15, 0), BookingStatus.COMPLETED)));

        assertThatCode(() -> bookingValidationService.validateDailySubjectCap(
                        student, tutorSubjectId, date, LocalTime.of(16, 0), LocalTime.of(17, 0)))
                .doesNotThrowAnyException();
    }

    private Booking buildBooking(LocalTime start, LocalTime end, BookingStatus status) {
        return Booking.builder()
                .bookingId(UUID.randomUUID())
                .sessionDate(date)
                .startTime(start)
                .endTime(end)
                .bookingStatus(status)
                .build();
    }
}