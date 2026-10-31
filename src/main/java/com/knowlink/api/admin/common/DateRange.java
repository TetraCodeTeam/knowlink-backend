package com.knowlink.api.admin.common;

import com.knowlink.api.shared.utils.AppTimeZone;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Inclusive calendar range. Queries use [fromInclusive, toExclusive).
 */
public record DateRange(LocalDate from, LocalDate to) {

    public LocalDateTime fromInclusive() {
        return fromDateTime(AppTimeZone.ZONE);
    }

    public LocalDateTime toExclusive() {
        return toExclusiveDateTime(AppTimeZone.ZONE);
    }

    public LocalDateTime fromDateTime(ZoneId zone) {
        return from.atStartOfDay(zone).toLocalDateTime();
    }

    public LocalDateTime toExclusiveDateTime(ZoneId zone) {
        return to.plusDays(1).atStartOfDay(zone).toLocalDateTime();
    }
}