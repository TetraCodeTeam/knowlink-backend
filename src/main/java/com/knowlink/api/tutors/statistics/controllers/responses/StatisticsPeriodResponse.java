package com.knowlink.api.tutors.statistics.controllers.responses;

import com.knowlink.api.tutors.statistics.data.enums.StatisticsPeriodType;

import java.time.LocalDate;

public record StatisticsPeriodResponse(StatisticsPeriodType type, LocalDate from, LocalDate to) {
}
