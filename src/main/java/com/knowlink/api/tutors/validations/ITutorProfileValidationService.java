package com.knowlink.api.tutors.validations;

import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.auth.controllers.requests.TutorSubjectRequest;
import com.knowlink.api.tutors.data.models.Subject;

import java.math.BigDecimal;
import java.util.UUID;

public interface ITutorProfileValidationService {
    void ifTutorProfileAlreadyExistsThrowException(User user);
    TutorProfile findTutorProfileOrThrowException(UUID tutorUserId);
    void ifTutorAlreadyTeachesSubjectThrowException(TutorProfile tutorProfile, Subject subject);
    void ifPaidSubjectHasInvalidPriceThrowException(TutorSubjectRequest request);
    TutorSubject findTutorSubjectOfTutorOrThrowException(TutorProfile tutorProfile, UUID tutorSubjectId);
    void ifPaidPriceIsInvalidThrowException(CompensationType compensationType, BigDecimal pricePerHour);
    void ifCareerChangeBlockedBySubjectsThrowException(TutorProfile tutorProfile, Career newCareer);
    void ifAddressRequiredForInPersonSubjectsThrowException(TutorProfile tutorProfile, String address);
    void ifSubjectHasActiveBookingsThrowException(TutorSubject tutorSubject);
}
