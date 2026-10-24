package com.knowlink.api.notifications.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTextFormatsTest {

    @Test
    @DisplayName("Monto entero - agrupa los miles con el formato es-AR")
    void amount_wholeNumber_groupsThousands() {
        assertThat(NotificationTextFormats.amount(new BigDecimal("2100"))).isEqualTo("$2.100");
    }

    @Test
    @DisplayName("Monto con centavos - conserva hasta dos decimales")
    void amount_withDecimals_keepsCents() {
        assertThat(NotificationTextFormats.amount(new BigDecimal("1234.5"))).isEqualTo("$1.234,5");
        assertThat(NotificationTextFormats.amount(new BigDecimal("1234.567"))).isEqualTo("$1.234,57");
    }

    @Test
    @DisplayName("Monto cero - sin decimales")
    void amount_zero() {
        assertThat(NotificationTextFormats.amount(BigDecimal.ZERO)).isEqualTo("$0");
    }

    @Test
    @DisplayName("Fecha con dia de la semana - con mayuscula inicial")
    void dateWithWeekday_capitalizesFirstLetter() {
        assertThat(NotificationTextFormats.dateWithWeekday(LocalDate.of(2026, 10, 20))).isEqualTo("Martes 20/10");
    }

    @Test
    @DisplayName("Fecha y hora - patrones fijos dd/MM/yyyy y HH:mm")
    void dateAndTime_useFixedPatterns() {
        assertThat(NotificationTextFormats.date(LocalDate.of(2026, 10, 20))).isEqualTo("20/10/2026");
        assertThat(NotificationTextFormats.time(LocalTime.of(15, 30))).isEqualTo("15:30");
    }
}