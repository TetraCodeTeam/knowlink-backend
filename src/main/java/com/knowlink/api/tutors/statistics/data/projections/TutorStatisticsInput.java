package com.knowlink.api.tutors.statistics.data.projections;

import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.statistics.utils.StatisticsPeriod;

import java.util.List;

/** Datos crudos del período que el calculador transforma en las métricas del panel. */
public record TutorStatisticsInput(
        StatisticsPeriod period,
        long completedSessionsPreviousPeriod,
        List<TutorBookingRow> bookings,
        List<TutorRatingRow> ratings,
        List<TutorSlotRow> offeredSlots,
        List<TutorSubject> subjects) {
}
