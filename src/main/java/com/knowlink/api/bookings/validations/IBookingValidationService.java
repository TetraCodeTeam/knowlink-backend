package com.knowlink.api.bookings.validations;

import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.users.data.models.User;
import java.time.LocalTime;
import java.util.UUID;

public interface IBookingValidationService {
    void validateTimeWithinSlot(TimeSlot timeSlot, LocalTime startTime, LocalTime endTime);

    void validateMinNotice(TutorProfile tutorProfile, TimeSlot timeSlot, LocalTime startTime);

    void validateNoOverlap(UUID timeSlotId, LocalTime startTime, LocalTime endTime);

    void validateSingleActiveHold(User student);

    void validateDailySubjectCap(User student, UUID tutorSubjectId, java.time.LocalDate date, LocalTime startTime,
            LocalTime endTime);

    void validateModality(Modality requested, Modality subjectModality);

    void validateOwnership(Booking booking, UUID userId);

    void validateCanSetVirtualLink(Booking booking, UUID tutorUserId);
    
    void validateCanConfirmSession(Booking booking, UUID tutorUserId);
}
