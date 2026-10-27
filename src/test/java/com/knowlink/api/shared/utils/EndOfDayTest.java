package com.knowlink.api.shared.utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class EndOfDayTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 28);

    @Test
    void endOfDayIsTheLatestTimeAndOnlyTheSentinelIsRecognizedAsIt() {
        assertThat(EndOfDay.isEndOfDay(EndOfDay.TIME)).isTrue();
        assertThat(EndOfDay.isEndOfDay(LocalTime.of(23, 59))).isFalse();
        assertThat(EndOfDay.isEndOfDay(LocalTime.MIDNIGHT)).isFalse();
        assertThat(EndOfDay.isEndOfDay(null)).isFalse();
        assertThat(EndOfDay.TIME).isAfter(LocalTime.of(23, 59, 59));
    }

    @Test
    void midnightAsAnEndBecomesTheSentinelAndOtherTimesAreUntouched() {
        assertThat(EndOfDay.normalizeEnd(LocalTime.MIDNIGHT)).isEqualTo(EndOfDay.TIME);
        assertThat(EndOfDay.normalizeEnd(LocalTime.of(22, 0))).isEqualTo(LocalTime.of(22, 0));
        assertThat(EndOfDay.normalizeEnd(null)).isNull();
    }

    @Test
    void sentinelSurvivesAMicrosecondPrecisionColumn() {
        // TIME(6) guarda hasta microsegundos: el valor no puede perder precisión ni redondear a 24:00.
        assertThat(EndOfDay.TIME.getNano() % 1_000).isZero();
    }

    @Test
    void endOfDayCountsAsMinute1440() {
        assertThat(EndOfDay.endMinuteOfDay(EndOfDay.TIME)).isEqualTo(1440);
        assertThat(EndOfDay.endMinuteOfDay(LocalTime.of(22, 30))).isEqualTo(22 * 60 + 30);
        assertThat(EndOfDay.endTimeOfMinute(1440)).isEqualTo(EndOfDay.TIME);
        assertThat(EndOfDay.endTimeOfMinute(23 * 60)).isEqualTo(LocalTime.of(23, 0));
    }

    @Test
    void durationsReachingEndOfDayAreExact() {
        assertThat(EndOfDay.between(LocalTime.of(23, 0), EndOfDay.TIME)).isEqualTo(Duration.ofHours(1));
        assertThat(EndOfDay.between(LocalTime.of(23, 30), EndOfDay.TIME)).isEqualTo(Duration.ofMinutes(30));
        assertThat(EndOfDay.between(LocalTime.of(9, 0), LocalTime.of(10, 0))).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void endOfDayInstantIsMidnightOfTheNextDay() {
        Instant expected = DATE.plusDays(1).atStartOfDay(AppTimeZone.ZONE).toInstant();

        assertThat(EndOfDay.toInstant(DATE, EndOfDay.TIME)).isEqualTo(expected);
        assertThat(EndOfDay.toInstant(DATE, LocalTime.of(10, 0)))
                .isEqualTo(DATE.atTime(10, 0).atZone(AppTimeZone.ZONE).toInstant());
    }

    @Test
    void midnightOfTheNextDayInstantIsReadBackAsEndOfDay() {
        Instant midnight = DATE.plusDays(1).atStartOfDay(AppTimeZone.ZONE).toInstant();

        assertThat(EndOfDay.endTimeFromInstant(midnight)).isEqualTo(EndOfDay.TIME);
        assertThat(EndOfDay.endTimeFromInstant(DATE.atTime(11, 0).atZone(AppTimeZone.ZONE).toInstant()))
                .isEqualTo(LocalTime.of(11, 0));
    }
}
