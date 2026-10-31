package com.knowlink.api.tutors.validations;

import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.auth.controllers.requests.TutorSubjectRequest;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.data.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TutorProfileValidationServiceImpl implements ITutorProfileValidationService {

    private final ITutorProfileRepository tutorProfileRepository;
    private final ITutorSubjectRepository tutorSubjectRepository;

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

    @Override
    public TutorSubject findTutorSubjectOfTutorOrThrowException(TutorProfile tutorProfile, UUID tutorSubjectId) {
        return tutorSubjectRepository
                .findByTutorSubjectIdAndTutorProfile_TutorProfileId(tutorSubjectId,
                        tutorProfile.getTutorProfileId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TUTOR_SUBJECT_NOT_FOUND",
                        "La materia seleccionada no existe.",
                        String.format("TutorSubject '%s' not found for TutorProfile '%s'", tutorSubjectId,
                                tutorProfile.getTutorProfileId())));
    }

    @Override
    public void ifPaidPriceIsInvalidThrowException(CompensationType compensationType, BigDecimal pricePerHour) {
        boolean isPaidWithInvalidPrice = compensationType == CompensationType.PAID
                && (pricePerHour == null || pricePerHour.signum() <= 0);

        if (isPaidWithInvalidPrice) {
            throw new ValidationException("El precio por hora debe ser mayor a cero.");
        }
    }

    @Override
    public void ifCareerChangeBlockedBySubjectsThrowException(TutorProfile tutorProfile, Career newCareer) {
        boolean careerChanged = !tutorProfile.getCareer().getCareerId().equals(newCareer.getCareerId());

        if (careerChanged && !tutorProfile.getSubjects().isEmpty()) {
            throw new ValidationException(
                    "No podés cambiar tu carrera mientras tengas materias registradas. Eliminá tus materias primero.");
        }
    }

    @Override
    public void ifAddressRequiredForInPersonSubjectsThrowException(TutorProfile tutorProfile, String address) {
        boolean hasInPersonSubject = tutorProfile.getSubjects().stream()
                .anyMatch(tutorSubject -> tutorSubject.getModality() == Modality.IN_PERSON
                        || tutorSubject.getModality() == Modality.BOTH);

        if (hasInPersonSubject && (address == null || address.isBlank())) {
            throw new ValidationException("La dirección es obligatoria para clases presenciales.");
        }
    }
}
