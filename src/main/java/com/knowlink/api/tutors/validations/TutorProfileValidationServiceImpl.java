package com.knowlink.api.tutors.validations;

import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.auth.controllers.requests.TutorSubjectRequest;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.users.data.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TutorProfileValidationServiceImpl implements ITutorProfileValidationService {

    private final ITutorProfileRepository tutorProfileRepository;

    @Override
    public void ifTutorProfileAlreadyExistsThrowException(User user) {
        if (tutorProfileRepository.existsByUser_UserId(user.getUserId())) {
            throw new DuplicateResourceException(
                    "DUPLICATE_TUTOR_PROFILE",
                    "El usuario ya tiene un perfil de tutor",
                    String.format("user con id '%s' ya tiene tutor profile", user.getUserId()));
        }
    }

    @Override
    public TutorProfile findTutorProfileOrThrowException(UUID tutorUserId) {
        return tutorProfileRepository.findByUserId(tutorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TUTOR_PROFILE_NOT_FOUND",
                        "Este tutor no está registrado.",
                        "TutorProfile not found for userId: " + tutorUserId));
    }

    @Override
    public void ifTutorAlreadyTeachesSubjectThrowException(TutorProfile tutorProfile, Subject subject) {
        boolean alreadyTeachesThisSubject = tutorProfile.getSubjects().stream()
                .anyMatch(tutorSubject -> tutorSubject.getSubject().getSubjectId().equals(subject.getSubjectId()));

        if (alreadyTeachesThisSubject) {
            throw new DuplicateResourceException(
                    "DUPLICATE_TUTOR_SUBJECT",
                    "Ya tenés esta materia cargada.",
                    String.format("TutorProfile con id '%s' ya tiene la materia '%s' cargada",
                            tutorProfile.getTutorProfileId(), subject.getName()));
        }
    }

    @Override
    public void ifPaidSubjectHasInvalidPriceThrowException(TutorSubjectRequest request) {
        boolean isPaidWithInvalidPrice = request.compensationType() == CompensationType.PAID
                && (request.pricePerHour() == null || request.pricePerHour().signum() <= 0);

        if (isPaidWithInvalidPrice) {
            throw new ValidationException(
                    String.format("El precio por hora debe ser mayor a cero para la materia '%s'.",
                            request.subjectName()));
        }
    }
}