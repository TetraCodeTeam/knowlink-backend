package com.knowlink.api.tutors.statistics.utils;

import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.tutors.statistics.data.enums.StatisticsPeriodType;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Período ya resuelto y validado, con ambos extremos inclusive. */
public record StatisticsPeriod(StatisticsPeriodType type, LocalDate from, LocalDate to) {

    public static StatisticsPeriod resolve(
            StatisticsPeriodType type, LocalDate customFrom, LocalDate customTo, LocalDate today) {
        return switch (type) {
            case LAST_MONTH -> new StatisticsPeriod(type, today.minusMonths(1), today);
            case LAST_3_MONTHS -> new StatisticsPeriod(type, today.minusMonths(2).withDayOfMonth(1), today);
            case THIS_YEAR -> new StatisticsPeriod(type, today.withDayOfYear(1), today);
            case CUSTOM -> resolveCustom(customFrom, customTo, today);
        };
    }

    private static StatisticsPeriod resolveCustom(LocalDate from, LocalDate to, LocalDate today) {
        if (from == null || to == null) {
            throw new ValidationException("Indicá la fecha de inicio y de fin del período.");
        }
        if (from.isAfter(to)) {
            throw new ValidationException("La fecha de inicio no puede ser posterior a la de fin.");
        }
        if (to.isAfter(today)) {
            throw new ValidationException("La fecha de fin no puede ser futura.");
        }
        if (from.isBefore(today.minusYears(TutorStatisticsConstants.MAX_LOOKBACK_YEARS))) {
            throw new ValidationException("Solo podés consultar los últimos "
                    + TutorStatisticsConstants.MAX_LOOKBACK_YEARS + " años.");
        }
        return new StatisticsPeriod(StatisticsPeriodType.CUSTOM, from, to);
    }

    /** Período inmediatamente anterior y de la misma duración, para comparar. */
    public StatisticsPeriod previous() {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousTo = from.minusDays(1);
        return new StatisticsPeriod(type, previousTo.minusDays(days - 1), previousTo);
    }
}
