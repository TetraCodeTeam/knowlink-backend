package com.knowlink.api.bookings.validations;

import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.repositories.IHoldRepository;
import com.knowlink.api.bookings.services.interfaces.IStudentScheduleValidationService;
import com.knowlink.api.bookings.utils.HoldTutorResolver;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.shared.utils.EndOfDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class HoldValidationServiceImplTest {

    @Mock
    private IHoldRepository holdRepository;
    @Mock
    private IBookingRepository bookingRepository;
    @Mock
    private IStudentScheduleValidationService studentScheduleValidationService;
    @Mock
    private HoldTutorResolver holdTutorResolver;

    @InjectMocks
    private HoldValidationServiceImpl holdValidationService;

    @Test
    @DisplayName("CP-HOLD.01 - acepta reservas de 1, 2 y 3 horas completas")
    void acceptsWholeHoursWithinRange() {
        assertThatCode(() -> holdValidationService.validateDuration(LocalTime.of(9, 0), LocalTime.of(10, 0)))
                .doesNotThrowAnyException();
        assertThatCode(() -> holdValidationService.validateDuration(LocalTime.of(9, 0), LocalTime.of(11, 0)))
                .doesNotThrowAnyException();
        assertThatCode(() -> holdValidationService.validateDuration(LocalTime.of(9, 0), LocalTime.of(12, 0)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CP-HOLD.02 - rechaza duraciones con fracción de hora")
    void rejectsPartialHours() {
        assertThatThrownBy(() -> holdValidationService.validateDuration(LocalTime.of(9, 0), LocalTime.of(10, 30)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("horas completas");
        assertThatThrownBy(() -> holdValidationService.validateDuration(LocalTime.of(9, 30), LocalTime.of(10, 0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("horas completas");
    }

    @Test
    @DisplayName("CP-HOLD.03 - rechaza reservas de más de 3 horas")
    void rejectsMoreThanThreeHours() {
        assertThatThrownBy(() -> holdValidationService.validateDuration(LocalTime.of(9, 0), LocalTime.of(13, 0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("máxima de una clase es de 3 horas");
    }

    @Test
    @DisplayName("CP-HOLD.04 - rechaza una reserva sin duración")
    void rejectsEmptyDuration() {
        assertThatThrownBy(() -> holdValidationService.validateDuration(LocalTime.of(9, 0), LocalTime.of(9, 0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("mínima de 1 hora");
    }

    @Test
    @DisplayName("CP-HOLD.05 - mide una reserva que termina a medianoche hasta el fin de día")
    void measuresWindowsEndingAtEndOfDay() {
        assertThatCode(() -> holdValidationService.validateDuration(LocalTime.of(22, 0), EndOfDay.TIME))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> holdValidationService.validateDuration(LocalTime.of(23, 30), EndOfDay.TIME))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("horas completas");
    }
}
