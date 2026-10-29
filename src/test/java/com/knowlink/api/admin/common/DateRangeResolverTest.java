package com.knowlink.api.admin.common;

import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.shared.utils.AppTimeZone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeResolverTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-28T15:00:00Z"), AppTimeZone.ZONE);
    private final DateRangeResolver resolver = new DateRangeResolver(CLOCK);

    @Test
    @DisplayName("Sin fechas devuelve los últimos 30 días incluyendo hoy")
    void resolve_withoutDates_returnsLast30Days() {
        DateRange range = resolver.resolve(null, null);

        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 10, 28));
    }

    @Test
    @DisplayName("Con un solo extremo informado devuelve 400")
    void resolve_withOnlyOneBound_throwsValidationException() {
        assertThatThrownBy(() -> resolver.resolve(LocalDate.of(2026, 10, 1), null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> resolver.resolve(null, LocalDate.of(2026, 10, 1)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Con fechas invertidas devuelve 400")
    void resolve_withInvertedRange_throwsValidationException() {
        assertThatThrownBy(() -> resolver.resolve(
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Un rango de 366 días es válido y uno de 367 no")
    void resolve_enforcesMaximumRange() {
        DateRange range = resolver.resolve(
                LocalDate.of(2025, 10, 28), LocalDate.of(2026, 10, 28));
        assertThat(range.from()).isEqualTo(LocalDate.of(2025, 10, 28));

        assertThatThrownBy(() -> resolver.resolve(
                LocalDate.of(2025, 10, 27), LocalDate.of(2026, 10, 28)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Las conversiones usan la zona horaria de la aplicación")
    void dateRange_convertsUsingApplicationTimeZone() {
        DateRange range = new DateRange(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 28));

        assertThat(range.fromInclusive()).isEqualTo(LocalDate.of(2026, 10, 28).atStartOfDay());
        assertThat(range.toExclusive()).isEqualTo(LocalDate.of(2026, 10, 29).atStartOfDay());
        assertThat(range.fromDateTime(ZoneId.of("UTC")))
                .isEqualTo(LocalDate.of(2026, 10, 28).atStartOfDay());
    }
}
