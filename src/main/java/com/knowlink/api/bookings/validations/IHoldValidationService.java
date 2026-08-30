package com.knowlink.api.bookings.validations;

import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.users.data.models.User;

import java.time.LocalTime;
import java.util.UUID;

public interface IHoldValidationService {
    void validateTimeWithinSlot(TimeSlot timeSlot, LocalTime startTime, LocalTime endTime);
    void validateExactDuration(LocalTime startTime, LocalTime endTime);
    void validateMinNotice(TutorProfile tutorProfile, TimeSlot timeSlot, LocalTime startTime);
    void validateNoOverlap(UUID timeSlotId, LocalTime startTime, LocalTime endTime);
    void validateSingleActiveHold(User student);
}