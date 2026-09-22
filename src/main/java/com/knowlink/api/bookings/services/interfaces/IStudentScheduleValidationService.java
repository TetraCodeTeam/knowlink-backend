package com.knowlink.api.bookings.services.interfaces;

import com.knowlink.api.users.data.models.User;

import java.time.LocalDate;
import java.time.LocalTime;

public interface IStudentScheduleValidationService {
    void validateNoTimeConflict(User student, LocalDate date, LocalTime startTime, LocalTime endTime);
}
