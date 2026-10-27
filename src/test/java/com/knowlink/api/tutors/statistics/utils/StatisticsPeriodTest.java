package com.knowlink.api.tutors.statistics.utils;

import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.tutors.statistics.data.enums.StatisticsPeriodType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatisticsPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @Test
    void lastMonthIsARollingMonthEndingToday() {
        StatisticsPeriod period = StatisticsPeriod.resolve(StatisticsPeriodType.LAST_MONTH, null, null, TODAY);

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 9, 22));
        assertThat(period.to()).isEqualTo(TODAY);
    }

    @Test
    void lastThreeMonthsStartsOnTheFirstDayOfTheMonthTwoMonthsBack() {
        StatisticsPeriod period = StatisticsPeriod.resolve(StatisticsPeriodType.LAST_3_MONTHS, null, null, TODAY);

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(period.to()).isEqualTo(TODAY);
    }

    @Test
    void thisYearStartsOnJanuaryFirst() {
        StatisticsPeriod period = StatisticsPeriod.resolve(StatisticsPeriodType.THIS_YEAR, null, null, TODAY);

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void quickPeriodsIgnoreCustomDates() {
        StatisticsPeriod period = StatisticsPeriod.resolve(
                StatisticsPeriodType.LAST_MONTH, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), TODAY);

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 9, 22));
    }

    @Test
    void customPeriodWithinTheLimitIsAccepted() {
        LocalDate oldest = TODAY.minusYears(TutorStatisticsConstants.MAX_LOOKBACK_YEARS);

        StatisticsPeriod period = StatisticsPeriod.resolve(StatisticsPeriodType.CUSTOM, oldest, TODAY, TODAY);

        assertThat(period.type()).isEqualTo(StatisticsPeriodType.CUSTOM);
        assertThat(period.from()).isEqualTo(oldest);
        assertThat(period.to()).isEqualTo(TODAY);
    }

    @Test
    void customPeriodRequiresBothDates() {
        assertThatThrownBy(() -> StatisticsPeriod.resolve(StatisticsPeriodType.CUSTOM, TODAY, null, TODAY))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> StatisticsPeriod.resolve(StatisticsPeriodType.CUSTOM, null, TODAY, TODAY))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void customPeriodRejectsInvertedRange() {
        assertThatThrownBy(() -> StatisticsPeriod.resolve(
                StatisticsPeriodType.CUSTOM, TODAY, TODAY.minusDays(1), TODAY))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void customPeriodRejectsFutureEnd() {
        assertThatThrownBy(() -> StatisticsPeriod.resolve(
                StatisticsPeriodType.CUSTOM, TODAY.minusDays(5), TODAY.plusDays(1), TODAY))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void customPeriodRejectsStartBeyondTheLookbackLimit() {
        LocalDate tooOld = TODAY.minusYears(TutorStatisticsConstants.MAX_LOOKBACK_YEARS).minusDays(1);

        assertThatThrownBy(() -> StatisticsPeriod.resolve(StatisticsPeriodType.CUSTOM, tooOld, TODAY, TODAY))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("3 años");
    }

    @Test
    void previousPeriodHasTheSameLengthAndEndsTheDayBefore() {
        StatisticsPeriod period = new StatisticsPeriod(
                StatisticsPeriodType.CUSTOM, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));

        StatisticsPeriod previous = period.previous();

        assertThat(previous.to()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(previous.from()).isEqualTo(LocalDate.of(2026, 9, 21));
    }
}
