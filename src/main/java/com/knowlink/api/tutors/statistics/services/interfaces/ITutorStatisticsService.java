package com.knowlink.api.tutors.statistics.services.interfaces;

import com.knowlink.api.tutors.statistics.controllers.responses.TutorStatisticsResponse;
import com.knowlink.api.tutors.statistics.data.enums.StatisticsPeriodType;

import java.time.LocalDate;
import java.util.UUID;

public interface ITutorStatisticsService {

    /** {@code from} y {@code to} solo se usan (y son obligatorios) cuando el período es CUSTOM. */
    TutorStatisticsResponse getStatistics(
            UUID tutorUserId, StatisticsPeriodType periodType, LocalDate from, LocalDate to);
}
