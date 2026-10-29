package com.knowlink.api.admin.common;

import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class DateRangeResolver {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_RANGE_DAYS = 366;

    private final Clock clock;

    /**
     * Resolves the requested range. Both null means the last 30 days including today.
     */
    public DateRange resolve(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            LocalDate today = LocalDate.now(clock);
            return new DateRange(today.minusDays(DEFAULT_DAYS - 1L), today);
        }
        if (from == null || to == null) {
            throw new ValidationException("Indicá ambas fechas del rango.");
        }
        if (from.isAfter(to)) {
            throw new ValidationException("La fecha desde no puede ser posterior a la fecha hasta.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new ValidationException("El rango de fechas no puede superar los 366 días.");
        }
        return new DateRange(from, to);
    }
}