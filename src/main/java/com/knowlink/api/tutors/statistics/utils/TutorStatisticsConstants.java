package com.knowlink.api.tutors.statistics.utils;

import com.knowlink.api.bookings.data.enums.BookingStatus;

import java.util.Set;

public final class TutorStatisticsConstants {

    private TutorStatisticsConstants() {
    }

    /** Máxima antigüedad consultable: acota el volumen de datos y coincide con la retención del histórico. */
    public static final int MAX_LOOKBACK_YEARS = 3;

    /** Estados que cuentan como horas reservadas (todo lo que no es cancelación ni reserva sin pagar). */
    public static final Set<BookingStatus> RESERVED_STATUSES = Set.of(
            BookingStatus.BOOKED,
            BookingStatus.IN_PROGRESS,
            BookingStatus.COMPLETED,
            BookingStatus.NOT_CONFIRMED,
            BookingStatus.NOT_FULFILLED_BY_TUTOR,
            BookingStatus.SESSION_NOT_HELD);

    /** Estados que cuentan como actividad del tutor en el período. */
    public static final Set<BookingStatus> ACTIVITY_STATUSES = Set.of(
            BookingStatus.BOOKED,
            BookingStatus.IN_PROGRESS,
            BookingStatus.COMPLETED,
            BookingStatus.CANCELLED,
            BookingStatus.NOT_CONFIRMED,
            BookingStatus.NOT_FULFILLED_BY_TUTOR,
            BookingStatus.SESSION_NOT_HELD);

    /** Fondos retenidos cuyo resultado final todavía no se registró (excluye reservas canceladas o vencidas). */
    public static final Set<BookingStatus> PENDING_RELEASE_STATUSES = Set.of(
            BookingStatus.BOOKED,
            BookingStatus.IN_PROGRESS,
            BookingStatus.COMPLETED,
            BookingStatus.NOT_CONFIRMED);
}
