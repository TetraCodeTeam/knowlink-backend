package com.knowlink.api.bookings.utils;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.users.data.models.User;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HoldTutorResolver {

    private final ITutorProfileRepository tutorProfileRepository;

    public User resolveTutor(TimeSlot timeSlot) {
        TutorProfile tutorProfile = tutorProfileRepository.findById(timeSlot.getTutorProfileId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TUTOR_PROFILE_NOT_FOUND",
                        "Este tutor no está registrado.",
                        "TutorProfile not found for id: " + timeSlot.getTutorProfileId()));
        return tutorProfile.getUser();
    }
}