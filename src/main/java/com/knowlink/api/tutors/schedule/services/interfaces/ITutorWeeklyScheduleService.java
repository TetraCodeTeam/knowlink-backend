package com.knowlink.api.tutors.schedule.services.interfaces;

import com.knowlink.api.tutors.schedule.controllers.responses.WeeklyScheduleResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface ITutorWeeklyScheduleService {
    WeeklyScheduleResponse getWeeklySchedule(UUID tutorUserId, LocalDate from, LocalDate to);
}
