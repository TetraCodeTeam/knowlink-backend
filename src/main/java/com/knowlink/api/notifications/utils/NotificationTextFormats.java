package com.knowlink.api.notifications.utils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shared formatting helpers for user-facing notification texts.
 */
public final class NotificationTextFormats {

    private static final DateTimeFormatter WEEKDAY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE d/M", new Locale("es", "ES"));
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private NotificationTextFormats() {
    }

    public static String dateWithWeekday(LocalDate date) {
        String formatted = date.format(WEEKDAY_DATE_FORMAT);
        return formatted.substring(0, 1).toUpperCase(Locale.ROOT) + formatted.substring(1);
    }

    public static String date(LocalDate date) {
        return date.format(DATE_FORMAT);
    }

    public static String time(LocalTime time) {
        return time.format(TIME_FORMAT);
    }

    public static String amount(BigDecimal amount) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(new Locale("es", "AR"));
        return "$" + new DecimalFormat("#,##0.##", symbols).format(amount);
    }
}