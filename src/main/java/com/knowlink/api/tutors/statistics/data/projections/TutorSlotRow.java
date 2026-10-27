package com.knowlink.api.tutors.statistics.data.projections;

import java.time.LocalDate;
import java.time.LocalTime;

public record TutorSlotRow(LocalDate date, LocalTime startTime, LocalTime endTime) {
}
