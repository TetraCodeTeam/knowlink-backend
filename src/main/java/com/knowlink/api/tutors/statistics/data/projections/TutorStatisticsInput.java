package com.knowlink.api.tutors.statistics.data.projections;

import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.statistics.utils.StatisticsPeriod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Datos crudos del período que el calculador transforma en las métricas del panel.
 * {@code activeClaimIdByBooking}: reclamo abierto (el más antiguo) de las reservas con fondos retenidos.
 */
public record TutorStatisticsInput(
        StatisticsPeriod period,
        long completedSessionsPreviousPeriod,
        List<TutorBookingRow> bookings,
        List<TutorRatingRow> ratings,
        List<TutorSlotRow> offeredSlots,
        List<TutorSubject> subjects,
        Map<UUID, UUID> activeClaimIdByBooking) {

    /** Sin información de reclamos: las métricas que no la usan no la necesitan. */
    public TutorStatisticsInput(
            StatisticsPeriod period,
            long completedSessionsPreviousPeriod,
            List<TutorBookingRow> bookings,
            List<TutorRatingRow> ratings,
            List<TutorSlotRow> offeredSlots,
            List<TutorSubject> subjects) {
        this(period, completedSessionsPreviousPeriod, bookings, ratings, offeredSlots, subjects, Map.of());
    }
}
