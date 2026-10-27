package com.knowlink.api.ratings.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class RatingWindowTest {

    private static final LocalDate SESSION_DATE = LocalDate.of(2026, 10, 20);
    private static final LocalTime END_TIME = LocalTime.of(16, 0);
    private static final LocalDateTime SESSION_END = LocalDateTime.of(SESSION_DATE, END_TIME);

    @Test
    @DisplayName("Sin confirmación: el plazo empieza al terminar la sesión")
    void withoutConfirmedAt_startsAtSessionEnd() {
        assertThat(RatingWindow.deadline(SESSION_DATE, END_TIME, null))
                .isEqualTo(SESSION_END.plusHours(24));
    }

    @Test
    @DisplayName("Confirmación previa al fin: sigue mandando el fin de la sesión")
    void confirmationBeforeSessionEnd_keepsSessionEndAsStart() {
        assertThat(RatingWindow.deadline(SESSION_DATE, END_TIME, SESSION_END.minusMinutes(30)))
                .isEqualTo(SESSION_END.plusHours(24));
    }

    @Test
    @DisplayName("Confirmación posterior al fin: el plazo se mueve a la confirmación")
    void confirmationAfterSessionEnd_movesStartToConfirmation() {
        LocalDateTime confirmedAt = SESSION_END.plusMinutes(8);

        assertThat(RatingWindow.deadline(SESSION_DATE, END_TIME, confirmedAt))
                .isEqualTo(confirmedAt.plusHours(24));
    }

    @Test
    @DisplayName("El borde exacto del plazo sigue abierto")
    void exactDeadline_isOpen() {
        LocalDateTime deadline = SESSION_END.plusHours(24);

        assertThat(RatingWindow.isOpen(deadline, deadline)).isTrue();
        assertThat(RatingWindow.isOpen(deadline, deadline.plusSeconds(1))).isFalse();
    }
}